package com.salmanlaghari.pkai.data.local.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
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
 */
@Singleton
class FlowMusicSecureStore @Inject constructor(
    @ApplicationContext context: Context
) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Returns the stored session JSON, or null when absent/blank. */
    fun getSessionJson(): String? =
        prefs.getString(KEY_SESSION_JSON, null)?.takeIf { it.isNotBlank() }

    fun saveSessionJson(sessionJson: String) {
        prefs.edit().putString(KEY_SESSION_JSON, sessionJson).apply()
    }

    fun clearSession() {
        prefs.edit().remove(KEY_SESSION_JSON).apply()
    }

    companion object {
        private const val PREFS_NAME = "flowmusic_secure_prefs"
        private const val KEY_SESSION_JSON = "flowmusic_session_json"
    }
}
