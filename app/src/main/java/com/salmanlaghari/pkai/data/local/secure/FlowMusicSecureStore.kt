package com.salmanlaghari.pkai.data.local.secure

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted storage for the Flow Music bridge session.
 *
 * The full Supabase session JSON contains a LONG-LIVED refresh token, so it
 * must never sit in plaintext: the old implementation kept it in plain
 * Preferences DataStore, which is exfiltratable via `adb backup` / cloud
 * transfer when `allowBackup="true"`. EncryptedSharedPreferences keeps the
 * value encrypted at rest with a key stored in the Android Keystore.
 *
 * Failure policy:
 * - READS never delete anything. Any init/decrypt failure degrades to "no
 *   session" and the keyset file is kept, so transient failures (keystore
 *   busy, device locked, I/O hiccup, provider init failure) can succeed on a
 *   later call instead of becoming irreversible data loss.
 * - WRITES recover from genuine corruption: if init fails with an
 *   unambiguous corruption signal ([AEADBadTagException] = bad/tampered
 *   value, [UnrecoverableKeyException] = invalidated key), the keyset file is
 *   dropped and init is retried once, so a corrupt file cannot permanently
 *   wedge the store. Anything else is left alone for a later retry.
 *
 * Callers never need their own try/catch around these methods.
 */
@Singleton
class FlowMusicSecureStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "FlowMusicSecureStore"
        private const val PREFS_NAME = "flowmusic_secure_prefs"
        private const val KEY_SESSION_JSON = "flowmusic_session_json"
    }

    private val lock = Any()

    @Volatile
    private var prefs: SharedPreferences? = null

    /** The init failure from the last [prefsOrNull] attempt, if any. */
    @Volatile
    private var lastInitError: Exception? = null

    /**
     * True only for UNAMBIGUOUS corruption signals. Everything else —
     * KeyStoreException, ProviderException, UserNotAuthenticatedException,
     * IOException, SecurityException — is treated as transient: the keyset
     * file is kept so a later call can succeed.
     *
     * The signal is searched along the whole CAUSE chain, not just the top
     * level: EncryptedSharedPreferences/Tink report keyset failures as
     * GeneralSecurityException("Could not read keyset") with the
     * AEADBadTagException as cause, and MasterKey failures surface as
     * ProviderException/KeyStoreException wrapping UnrecoverableKeyException.
     */
    private fun isCorruptionSignal(e: Exception): Boolean {
        var cur: Throwable? = e
        var depth = 0
        while (cur != null && depth < 10) {
            if (cur is AEADBadTagException || cur is UnrecoverableKeyException) return true
            cur = cur.cause
            depth++
        }
        return false
    }

    private fun prefsOrNull(): SharedPreferences? {
        prefs?.let { return it }
        synchronized(lock) {
            prefs?.let { return it }
            return try {
                createPrefs().also {
                    prefs = it
                    lastInitError = null
                }
            } catch (e: Exception) {
                // Read path: NEVER delete. Report "no session" and remember
                // why, so the write path can decide about recovery.
                Log.w(TAG, "Encrypted prefs unavailable; keeping keyset", e)
                lastInitError = e
                null
            }
        }
    }

    private fun createPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun deleteKeysetFile() {
        try {
            val f = File(context.applicationInfo.dataDir, "shared_prefs/$PREFS_NAME.xml")
            if (f.exists() && !f.delete()) {
                Log.w(TAG, "Could not delete unreadable keyset file")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Keyset cleanup failed: ${e.message}")
        }
    }

    /** Returns the stored session JSON, or null when absent/blank/unreadable. */
    fun getSessionJson(): String? {
        return try {
            prefsOrNull()?.getString(KEY_SESSION_JSON, null)?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "getSessionJson failed; treating as absent", e)
            null
        }
    }

    fun saveSessionJson(sessionJson: String) {
        try {
            val existing = prefsOrNull()
            if (existing != null) {
                existing.edit().putString(KEY_SESSION_JSON, sessionJson).apply()
                return
            }
            // Init failed. If the failure is a PROVABLE corruption signal,
            // drop the keyset and retry once — otherwise a corrupt file would
            // wedge the store forever. Transient failures are left alone.
            val err = lastInitError
            if (err != null && isCorruptionSignal(err)) {
                Log.w(TAG, "Dropping corrupt keyset and retrying save", err)
                deleteKeysetFile()
                synchronized(lock) {
                    prefs = null
                    lastInitError = null
                }
                prefsOrNull()?.edit()?.putString(KEY_SESSION_JSON, sessionJson)?.apply()
                    ?: Log.w(TAG, "saveSessionJson skipped: encrypted prefs unavailable after recovery")
            } else {
                Log.w(TAG, "saveSessionJson skipped: encrypted prefs unavailable (transient)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "saveSessionJson failed", e)
        }
    }

    fun clearSession() {
        try {
            prefsOrNull()?.edit()?.remove(KEY_SESSION_JSON)?.apply()
        } catch (e: Exception) {
            Log.w(TAG, "clearSession failed", e)
        }
    }
}
