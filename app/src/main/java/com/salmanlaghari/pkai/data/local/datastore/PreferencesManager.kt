package com.salmanlaghari.pkai.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "pk_ai_settings")

data class UserSession(
    val isLoggedIn: Boolean,
    val isGuest: Boolean,
    val userId: String?,
    val displayName: String?,
    val email: String?,
    val profileImageUrl: String?
)

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")
    private val isLoggedInKey = booleanPreferencesKey("user_is_logged_in")
    private val isGuestKey = booleanPreferencesKey("user_is_guest")
    private val userIdKey = stringPreferencesKey("user_id")
    private val displayNameKey = stringPreferencesKey("user_display_name")
    private val emailKey = stringPreferencesKey("user_email")
    private val profileImageUrlKey = stringPreferencesKey("user_profile_image_url")

    val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[onboardingCompletedKey] ?: false
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[onboardingCompletedKey] = completed
        }
    }

    val userSession: Flow<UserSession> = context.dataStore.data.map { preferences ->
        UserSession(
            isLoggedIn = preferences[isLoggedInKey] ?: false,
            isGuest = preferences[isGuestKey] ?: false,
            userId = preferences[userIdKey],
            displayName = preferences[displayNameKey],
            email = preferences[emailKey],
            profileImageUrl = preferences[profileImageUrlKey]
        )
    }

    suspend fun saveUserSession(userId: String, displayName: String?, email: String?, profileImageUrl: String?) {
        context.dataStore.edit { preferences ->
            preferences[isLoggedInKey] = true
            preferences[isGuestKey] = false
            preferences[userIdKey] = userId
            preferences[displayNameKey] = displayName ?: ""
            preferences[emailKey] = email ?: ""
            preferences[profileImageUrlKey] = profileImageUrl ?: ""
        }
    }

    suspend fun saveGuestSession() {
        context.dataStore.edit { preferences ->
            preferences[isLoggedInKey] = true
            preferences[isGuestKey] = true
            preferences[userIdKey] = "guest_user"
            preferences[displayNameKey] = "Guest User"
            preferences[emailKey] = ""
            preferences[profileImageUrlKey] = ""
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences[isLoggedInKey] = false
            preferences[isGuestKey] = false
            preferences[userIdKey] = ""
            preferences[displayNameKey] = ""
            preferences[emailKey] = ""
            preferences[profileImageUrlKey] = ""
        }
    }
}
