package com.salmanlaghari.pkai.ui.aihub

import android.util.Log
import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
 *    persisted. When the same Google account already owns a Flow Music
 *    account, Supabase signs it into that EXISTING user automatically.
 *  - Every later AI Hub open calls [getValidSessionJson], which returns the
 *    cached session while it is still valid and otherwise refreshes it
 *    SILENTLY with the refresh token. The user never sees another sign-in
 *    popup for the music engine.
 */
@Singleton
class FlowMusicSessionManager @Inject constructor(
    private val preferencesManager: PreferencesManager
) {

    companion object {
        private const val TAG = "FlowMusicSession"

        /** Refresh ahead of the real expiry so injection never races it. */
        private const val EXPIRY_BUFFER_SEC = 120L
    }

    /**
     * Exchanges a fresh Google ID token (from the PK-AI sign-in) for a real
     * Flow Music backend session and persists it.
     *
     * @return true when the bridge is now connected.
     */
    suspend fun connectWithIdToken(idToken: String, email: String?): Boolean =
        withContext(Dispatchers.IO) {
            val session = FlowMusicOAuth.exchangeIdTokenForSession(idToken)
            if (session == null) {
                Log.w(TAG, "ID-token exchange failed for $email")
                return@withContext false
            }
            preferencesManager.saveFlowMusicSessionJson(session.toString())
            Log.i(TAG, "Flow Music bridge connected for $email")
            true
        }

    /**
     * Returns a usable session JSON: the cached one while still valid,
     * otherwise silently refreshed via the refresh token.
     *
     * @return null when there is no session or the refresh token was
     * rejected (caller should fall back to the manual connect flow).
     */
    suspend fun getValidSessionJson(): JSONObject? = withContext(Dispatchers.IO) {
        val stored = preferencesManager.flowMusicSessionJson.first()
            ?: return@withContext null
        val session = try {
            JSONObject(stored)
        } catch (e: Exception) {
            Log.w(TAG, "Stored Flow Music session is corrupt; clearing")
            preferencesManager.clearFlowMusicSession()
            return@withContext null
        }

        val nowSec = System.currentTimeMillis() / 1000L
        val expiresAt = session.optLong("expires_at", 0L)
        if (expiresAt - nowSec > EXPIRY_BUFFER_SEC) {
            return@withContext session
        }

        val refreshToken = session.optString("refresh_token", "")
        if (refreshToken.isBlank()) {
            preferencesManager.clearFlowMusicSession()
            return@withContext null
        }
        val refreshed = FlowMusicOAuth.refreshSession(refreshToken)
        if (refreshed == null) {
            // Refresh rejected (revoked / rotated) - drop it so the UI can
            // fall back to the manual connect flow.
            preferencesManager.clearFlowMusicSession()
            return@withContext null
        }
        preferencesManager.saveFlowMusicSessionJson(refreshed.toString())
        Log.i(TAG, "Flow Music bridge session refreshed silently")
        refreshed
    }

    /**
     * Persists an already-obtained session (e.g. from the manual Custom Tab
     * flow) so the silent auto-connect can revive it later.
     */
    suspend fun connectWithSessionJson(session: JSONObject): Boolean =
        withContext(Dispatchers.IO) {
            if (session.optString("access_token", "").isBlank() ||
                session.optString("refresh_token", "").isBlank()
            ) {
                return@withContext false
            }
            preferencesManager.saveFlowMusicSessionJson(session.toString())
            Log.i(TAG, "Flow Music bridge session persisted")
            true
        }

    /** True when a bridge session is persisted (regardless of expiry). */
    suspend fun hasSession(): Boolean =
        preferencesManager.flowMusicSessionJson.first() != null

    suspend fun clear() {
        preferencesManager.clearFlowMusicSession()
    }
}
