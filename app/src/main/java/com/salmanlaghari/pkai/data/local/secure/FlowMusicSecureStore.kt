package com.salmanlaghari.pkai.data.local.secure

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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
 * Failure policy: every Keystore failure mode (key invalidated after a
 * lock-screen change, keyset present but master key missing after a
 * restore/transfer, tampered value) is caught here. The store then degrades
 * to "no session" instead of crashing the app, and drops the unreadable
 * keyset file so the next write starts clean. Callers therefore never need
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

    /** Set once the keyset proves unreadable; avoids retrying a doomed init. */
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
                // MasterKey build / EncryptedSharedPreferences.create /
                // decrypt can all throw (GeneralSecurityException,
                // SecurityException). Never crash the caller: drop the
                // unreadable keyset so a later write recreates it, and
                // report "no session" until then.
                Log.w(TAG, "Encrypted prefs unavailable; dropping keyset", e)
                deleteKeysetFile()
                keysetBroken = true
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
