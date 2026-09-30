package com.salmanlaghari.pkai.data.local.secure

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.UserNotAuthenticatedException
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.GeneralSecurityException
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
 * Failure policy: a PROVABLY corrupt keyset (GeneralSecurityException from a
 * bad/tampered keyset or an invalidated key) is dropped so the next write
 * starts clean, and reads degrade to "no session" instead of crashing.
 * TRANSIENT failures (keystore busy, device locked while the key needs auth,
 * I/O hiccups) must NOT delete the file: the keyset is kept so a later call
 * can still succeed, and no sticky flag is set. Callers therefore never need
 * their own try/catch around these methods.
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

    /** Set once the keyset proves CORRUPT; avoids retrying a doomed init. */
    @Volatile
    private var keysetBroken = false

    private fun prefsOrNull(): SharedPreferences? {
        prefs?.let { return it }
        if (keysetBroken) return null
        synchronized(lock) {
            prefs?.let { return it }
            if (keysetBroken) return null
            return try {
                createPrefs().also { prefs = it }
            } catch (e: Exception) {
                // Classify before acting: only a provably corrupt keyset is
                // dropped. Transient failures keep the file so a later call
                // can succeed, and set no sticky flag.
                val corruptKeyset = e is GeneralSecurityException &&
                    e !is UserNotAuthenticatedException
                if (corruptKeyset) {
                    // Bad/tampered keyset or invalidated key: drop the file so
                    // the next write recreates it, and stop retrying init.
                    Log.w(TAG, "Encrypted prefs keyset corrupt; dropping keyset", e)
                    deleteKeysetFile()
                    keysetBroken = true
                } else {
                    // Transient: keystore busy, device locked (auth needed),
                    // I/O hiccup. Keep the file; report "no session" for now.
                    Log.w(TAG, "Encrypted prefs temporarily unavailable; keeping keyset", e)
                }
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
            // If a previous init marked the keyset broken, allow one fresh
            // attempt now that there is actually something to persist.
            if (keysetBroken) {
                synchronized(lock) { keysetBroken = false }
                prefs = null
            }
            prefsOrNull()?.edit()?.putString(KEY_SESSION_JSON, sessionJson)?.apply()
                ?: Log.w(TAG, "saveSessionJson skipped: encrypted prefs unavailable")
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
