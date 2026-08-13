package com.salmanlaghari.pkai.data.repository

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthEmailException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import com.salmanlaghari.pkai.data.local.datastore.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val firebaseAuth: FirebaseAuth
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
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val user = authResult.user!!

            preferencesManager.saveUserSession(
                userId = user.uid,
                displayName = user.displayName,
                email = user.email,
                profileImageUrl = user.photoUrl?.toString()
            )

            Result.success(
                UserSession(
                    isLoggedIn = true,
                    isGuest = false,
                    userId = user.uid,
                    displayName = user.displayName,
                    email = user.email,
                    profileImageUrl = user.photoUrl?.toString()
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception(friendlyAuthMessage(e)))
        }
    }

    override suspend fun loginWithEmailPassword(email: String, password: String): Result<UserSession> {
        return try {
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val user = authResult.user!!

            preferencesManager.saveUserSession(
                userId = user.uid,
                displayName = user.displayName,
                email = user.email,
                profileImageUrl = user.photoUrl?.toString()
            )

            Result.success(
                UserSession(
                    isLoggedIn = true,
                    isGuest = false,
                    userId = user.uid,
                    displayName = user.displayName,
                    email = user.email,
                    profileImageUrl = user.photoUrl?.toString()
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception(friendlyAuthMessage(e)))
        }
    }

    override suspend fun registerWithEmailPassword(
        username: String,
        email: String,
        password: String
    ): Result<UserSession> {
        return try {
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val user = authResult.user!!

            val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(username)
                .build()
            user.updateProfile(profileUpdates).await()

            preferencesManager.saveUserSession(
                userId = user.uid,
                displayName = username,
                email = user.email,
                profileImageUrl = null
            )

            Result.success(
                UserSession(
                    isLoggedIn = true,
                    isGuest = false,
                    userId = user.uid,
                    displayName = username,
                    email = user.email,
                    profileImageUrl = null
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception(friendlyAuthMessage(e)))
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
            firebaseAuth.signOut()
            preferencesManager.clearSession()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Maps raw Firebase Auth exceptions to clear, user-friendly messages.
     */
    private fun friendlyAuthMessage(e: Exception): String {
        return when (e) {
            is FirebaseAuthInvalidUserException ->
                "No account found for this email. Please sign up first."
            is FirebaseAuthInvalidCredentialsException ->
                "Incorrect email or password. Please try again."
            is FirebaseAuthUserCollisionException ->
                "An account with this email already exists. Please log in instead."
            is FirebaseAuthWeakPasswordException ->
                "Password is too weak. Use at least 6 characters, including letters and numbers."
            is FirebaseAuthEmailException ->
                "The email address is badly formatted. Please check and try again."
            is FirebaseNetworkException ->
                "Network error. Check your internet connection and try again."
            else -> e.localizedMessage ?: "Authentication failed. Please try again."
        }
    }
}