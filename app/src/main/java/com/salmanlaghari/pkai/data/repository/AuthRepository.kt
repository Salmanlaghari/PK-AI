package com.salmanlaghari.pkai.data.repository

import com.salmanlaghari.pkai.data.local.datastore.UserSession
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun getSession(): Flow<UserSession>
    suspend fun loginWithGoogle(idToken: String, displayName: String?, email: String?, photoUrl: String?): Result<UserSession>
    suspend fun loginAsGuest(): Result<UserSession>
    suspend fun logout(): Result<Unit>
}
