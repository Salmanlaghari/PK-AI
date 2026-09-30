package com.salmanlaghari.pkai.data.repository

import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import com.salmanlaghari.pkai.data.local.datastore.UserSession
import com.salmanlaghari.pkai.data.local.secure.FlowMusicSecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val flowMusicSecureStore: FlowMusicSecureStore
) : AuthRepository {

    override fun getSession(): Flow<UserSession> {
        return preferencesManager.userSession
    }

    override suspend fun loginWithGoogle(
        idToken: String,
        displayName: String?,
        email: String?,
        photoUrl: String?
    ): Result<UserSession> {
        return try {
            if (idToken.isBlank()) {
                return Result.failure(IllegalArgumentException("ID Token cannot be empty"))
            }
            // Parse token or standard user details
            val userId = "google_$idToken" // Secure representation for local session
            preferencesManager.resetGuestMessageCount()
            preferencesManager.saveUserSession(
                userId = userId,
                displayName = displayName,
                email = email,
                profileImageUrl = photoUrl
            )
            Result.success(
                UserSession(
                    isLoggedIn = true,
                    isGuest = false,
                    userId = userId,
                    displayName = displayName,
                    email = email,
                    profileImageUrl = photoUrl
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loginAsGuest(): Result<UserSession> {
        return try {
            preferencesManager.saveGuestSession()
            Result.success(
                UserSession(
                    isLoggedIn = true,
                    isGuest = true,
                    userId = "guest_user",
                    displayName = "Guest User",
                    email = "",
                    profileImageUrl = ""
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            preferencesManager.clearSession()
            // Signing out of PK-AI also disconnects the Flow Music bridge.
            // The encrypted store touches the Keystore on first use, so keep
            // it off the caller's thread (logout is invoked from Main).
            withContext(Dispatchers.IO) {
                flowMusicSecureStore.clearSession()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
