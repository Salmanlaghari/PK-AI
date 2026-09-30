package com.salmanlaghari.pkai.ui.aihub

import android.util.Log
import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import com.salmanlaghari.pkai.data.local.secure.FlowMusicSecureStore
import com.salmanlaghari.pkai.ui.aihub.FlowMusicOAuth.RefreshResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the Flow Music bridge session lifecycle.
 *
 * Design (no Browse UI, no repeated popups):
 *  - At PK-AI Google sign-up the fresh ID token is exchanged ONCE for a real
 *    Supabase session ([connectWithIdToken]) and the full session JSON is
 *    persisted in ENCRYPTED storage ([FlowMusicSecureStore]) - the refresh
 *    token is long-lived and must never sit in plaintext. When the same
 *    Google account already owns a Flow Music account, Supabase signs it
 *    into that EXISTING user automatically.
 *  - Every later AI Hub open calls [getValidSessionJson], which returns the
 *    cached session while it is still valid and otherwise refreshes it
 *    SILENTLY with the refresh token. The user never sees another sign-in
 *    popup for the music engine.
 *
 * Refresh semantics: the stored session is dropped ONLY when the backend
 * explicitly rejects the refresh token ([RefreshResult.AuthRejected]).
 * Transport failures ([RefreshResult.TransportError]) keep the stored
 * session so a transient network blip does not force manual re-auth.
 */
@Singleton
class FlowMusicSessionManager @Inject constructor(
    private val secureStore: FlowMusicSecureStore,
    private val preferencesManager: PreferencesManager
) {

    companion object {
        private const val TAG = "FlowMusicSession"

        /** Refresh ahead of the real expiry so injection never races it. */
        private const val EXPIRY_BUFFER_SEC = 120L
    }

    /** Serializes refresh-token rotation so concurrent callers cannot race. */
    private val refreshMutex = Mutex()

    /**
     * Deletes the legacy PLAINTEXT session left by pre-#88 builds (once).
     * Runs under the session mutex so it cannot interleave with a
     * concurrent write/clear.
     */
    private suspend fun ensureLegacySessionMigrated() {
        try {
            preferencesManager.migrateLegacyFlowMusicSession()
        } catch (e: Exception) {
            Log.w(TAG, "Legacy Flow Music session migration skipped: ${e.message}")
        }
    }

    /**
     * Exchanges a fresh Google ID token (from the PK-AI sign-in) for a real
     * Flow Music backend session and persists it.
     *
     * @return true when the bridge is now connected.
     */
    suspend fun connectWithIdToken(idToken: String): Boolean =
        withContext(Dispatchers.IO) {
            refreshMutex.withLock {
                ensureLegacySessionMigrated()
                val session = FlowMusicOAuth.exchangeIdTokenForSession(idToken)
                if (session == null) {
                    Log.w(TAG, "ID-token exchange failed")
                    return@withLock false
                }
                secureStore.saveSessionJson(session.toString())
                Log.i(TAG, "Flow Music bridge connected")
                true
            }
        }

    /**
     * Returns a usable session JSON: the cached one while still valid,
     * otherwise silently refreshed via the refresh token.
     *
     * @return null when there is no session, the refresh token was rejected
     * (caller should fall back to the manual connect flow), or the refresh
     * hit a transport error (the stored session is kept for a later retry).
     */
    /** Synchronous best-effort check: is there a stored session right now? */
    fun hasStoredSession(): Boolean = secureStore.getSessionJson()?.isNotBlank() == true

    suspend fun getValidSessionJson(): JSONObject? = withContext(Dispatchers.IO) {
        refreshMutex.withLock { ensureLegacySessionMigrated() }
        val stored = secureStore.getSessionJson() ?: return@withContext null
        val session = try {
            JSONObject(stored)
        } catch (e: Exception) {
            Log.w(TAG, "Stored Flow Music session is corrupt; clearing")
            secureStore.clearSession()
            return@withContext null
        }

        val nowSec = System.currentTimeMillis() / 1000L
        val expiresAt = session.optLong("expires_at", 0L)
        if (expiresAt - nowSec > EXPIRY_BUFFER_SEC) {
            return@withContext session
        }

        refreshMutex.withLock {
            // Re-read inside the lock: another caller may have refreshed
            // while this one was waiting.
            val current = secureStore.getSessionJson() ?: return@withLock null
            val currentSession = try {
                JSONObject(current)
            } catch (e: Exception) {
                secureStore.clearSession()
                return@withLock null
            }
            val currentExpiresAt = currentSession.optLong("expires_at", 0L)
            if (currentExpiresAt - System.currentTimeMillis() / 1000L > EXPIRY_BUFFER_SEC) {
                return@withLock currentSession
            }
            val refreshToken = currentSession.optString("refresh_token", "")
            if (refreshToken.isBlank()) {
                secureStore.clearSession()
                return@withLock null
            }
            when (val result = FlowMusicOAuth.refreshSession(refreshToken)) {
                is RefreshResult.Success -> {
                    secureStore.saveSessionJson(result.session.toString())
                    Log.i(TAG, "Flow Music bridge session refreshed silently")
                    result.session
                }
                is RefreshResult.AuthRejected -> {
                    // Refresh token revoked / rotated - drop it so the UI can
                    // fall back to the manual connect flow.
                    secureStore.clearSession()
                    null
                }
                is RefreshResult.TransportError -> {
                    // Keep the stored session; a later attempt will retry.
                    Log.w(TAG, "Session refresh hit a transport error; keeping stored session")
                    null
                }
            }
        }
    }

    /**
     * Persists an already-obtained session (e.g. from the manual Custom Tab
     * flow) so the silent auto-connect can revive it later.
     */
    suspend fun connectWithSessionJson(session: JSONObject): Boolean =
        withContext(Dispatchers.IO) {
            refreshMutex.withLock {
                ensureLegacySessionMigrated()
                if (session.optString("access_token", "").isBlank() ||
                    session.optString("refresh_token", "").isBlank()
                ) {
                    return@withLock false
                }
                secureStore.saveSessionJson(session.toString())
                Log.i(TAG, "Flow Music bridge session persisted")
                true
            }
        }

    suspend fun clear() {
        withContext(Dispatchers.IO) {
            // Hold the mutex so an in-flight refresh cannot re-persist the
            // session right after sign-out clears it.
            refreshMutex.withLock {
                ensureLegacySessionMigrated()
                secureStore.clearSession()
            }
        }
    }
}
