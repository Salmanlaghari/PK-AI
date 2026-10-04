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
 * Encrypted storage for the user's own Gemini API key (BYOK).
 *
 * The key comes from the user's own Google AI Studio account, so each user's
 * requests consume their own quota. It must never sit in plaintext:
 * EncryptedSharedPreferences keeps the value encrypted at rest with a key
 * stored in the Android Keystore.
 *
 * Failure policy (same as [FlowMusicSecureStore]):
 * - READS never delete anything. Any init/decrypt failure degrades to "no
 *   key" and the keyset file is kept, so transient failures (keystore busy,
 *   device locked, I/O hiccup, provider init failure) can succeed later.
 * - WRITES recover from genuine corruption ([AEADBadTagException] or
 *   [UnrecoverableKeyException] anywhere in the cause chain): the keyset file
 *   is dropped and init is retried once, so a corrupt file cannot permanently
 *   wedge the store. Anything else is left alone for a later retry.
 *
 * Callers never need their own try/catch around these methods, and the raw
 * key value is never logged.
 */
@Singleton
class GeminiKeyStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "GeminiKeyStore"
        private const val PREFS_NAME = "gemini_secure_prefs"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    }

    private val lock = Any()

    @Volatile
    private var prefs: SharedPreferences? = null

    /** The init failure from the last [prefsOrNull] attempt, if any. */
    @Volatile
    private var lastInitError: Exception? = null

    /**
     * In-memory copy of the key so [getApiKey] doesn't hit EncryptedSharedPreferences
     * (MasterKey + disk I/O) on every call — AiProviderFactory.keyFor reads this on the
     * main thread for every message. Populated on the first successful read, refreshed on
     * save, cleared on clear. Never logged.
     */
    @Volatile
    private var cachedKey: String? = null

    /** True once [cachedKey] reflects a successful store read/write. */
    @Volatile
    private var keyCacheLoaded: Boolean = false

    private fun updateCache(value: String?) {
        cachedKey = value
        keyCacheLoaded = true
    }

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

    /**
     * Returns the user's stored Gemini API key, or null when absent/blank/unreadable.
     * The value is never logged. Served from an in-memory cache after the first
     * successful read so main-thread callers never pay the disk/keystore cost.
     */
    fun getApiKey(): String? {
        if (keyCacheLoaded) return cachedKey
        synchronized(lock) {
            if (keyCacheLoaded) return cachedKey
            return try {
                val prefs = prefsOrNull()
                val key = prefs?.getString(KEY_GEMINI_API_KEY, null)?.takeIf { it.isNotBlank() }
                // Cache only on a genuinely successful read: a transient init
                // failure must stay retryable per the failure policy above.
                if (prefs != null) updateCache(key)
                key
            } catch (e: Exception) {
                Log.w(TAG, "getApiKey failed; treating as absent", e)
                null
            }
        }
    }

    /** True when a user key is currently stored. The key itself is never exposed here. */
    fun hasApiKey(): Boolean = getApiKey() != null

    /**
     * Stores the user's Gemini API key. Trims whitespace; blank values are rejected.
     *
     * @return true only when the key was actually persisted; false when the
     * value was blank or the encrypted store was unavailable.
     */
    fun saveApiKey(apiKey: String): Boolean {
        val value = apiKey.trim()
        if (value.isBlank()) {
            Log.w(TAG, "saveApiKey skipped: blank key")
            return false
        }
        return try {
            val existing = prefsOrNull()
            if (existing != null) {
                existing.edit().putString(KEY_GEMINI_API_KEY, value).apply()
                updateCache(value)
                return true
            }
            val err = lastInitError
            if (err != null && isCorruptionSignal(err)) {
                Log.w(TAG, "Dropping corrupt keyset and retrying save", err)
                deleteKeysetFile()
                synchronized(lock) {
                    prefs = null
                    lastInitError = null
                }
                val recovered = prefsOrNull()
                if (recovered != null) {
                    recovered.edit().putString(KEY_GEMINI_API_KEY, value).apply()
                    updateCache(value)
                    true
                } else {
                    Log.w(TAG, "saveApiKey skipped: encrypted prefs unavailable after recovery")
                    false
                }
            } else {
                Log.w(TAG, "saveApiKey skipped: encrypted prefs unavailable (transient)")
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "saveApiKey failed", e)
            false
        }
    }

    /**
     * Removes the stored key.
     *
     * @return true only when the key was actually removed (store reachable);
     * false when the encrypted store was unavailable.
     */
    fun clearApiKey(): Boolean {
        return try {
            val prefs = prefsOrNull()
            if (prefs != null) {
                prefs.edit().remove(KEY_GEMINI_API_KEY).apply()
                updateCache(null)
                true
            } else {
                Log.w(TAG, "clearApiKey skipped: encrypted prefs unavailable (transient)")
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "clearApiKey failed", e)
            false
        }
    }
}
