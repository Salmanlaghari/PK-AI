package com.salmanlaghari.pkai.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.salmanlaghari.pkai.data.model.FreeAiModel
import com.salmanlaghari.pkai.data.model.LlmProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    // Settings Keys
    private val isDarkModeKey = booleanPreferencesKey("is_dark_mode")
    private val appLanguageKey = stringPreferencesKey("app_language")
    private val notificationsEnabledKey = booleanPreferencesKey("notifications_enabled")
    private val appThemeKey = stringPreferencesKey("app_theme")

    // AI provider selection
    private val selectedProviderIdKey = stringPreferencesKey("selected_provider_id")

    val selectedProviderId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[selectedProviderIdKey] ?: LlmProvider.DEFAULT.id
    }

    suspend fun setSelectedProviderId(providerId: String) {
        context.dataStore.edit { preferences ->
            preferences[selectedProviderIdKey] = providerId
        }
    }

    // Free AI tab (key-less) model selection
    private val selectedFreeModelIdKey = stringPreferencesKey("selected_free_model_id")

    val selectedFreeModelId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[selectedFreeModelIdKey] ?: FreeAiModel.DEFAULT.id
    }

    suspend fun setSelectedFreeModelId(freeModelId: String) {
        context.dataStore.edit { preferences ->
            preferences[selectedFreeModelIdKey] = freeModelId
        }
    }

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

    // Guest message limit (10 AI messages for guest users)
    private val guestMessageCountKey = intPreferencesKey("guest_message_count")

    val guestMessageCount: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[guestMessageCountKey] ?: 0
    }

    suspend fun incrementGuestMessageCount() {
        context.dataStore.edit { preferences ->
            preferences[guestMessageCountKey] = (preferences[guestMessageCountKey] ?: 0) + 1
        }
    }

    suspend fun resetGuestMessageCount() {
        context.dataStore.edit { preferences ->
            preferences[guestMessageCountKey] = 0
        }
    }

    // Settings Streams & Setters
    val isDarkMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[isDarkModeKey] ?: true // Defaults to true for PK AI Premium Dark style
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[isDarkModeKey] = enabled
        }
    }

    val appLanguage: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[appLanguageKey] ?: "en" // Defaults to English
    }

    suspend fun setAppLanguage(languageCode: String) {
        context.dataStore.edit { preferences ->
            preferences[appLanguageKey] = languageCode
        }
    }

    val isNotificationsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[notificationsEnabledKey] ?: true // Defaults to true
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[notificationsEnabledKey] = enabled
        }
    }

    // Theme (aurora | ocean | sunset)
    val appTheme: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[appThemeKey] ?: "aurora" // Defaults to Aurora
    }

    suspend fun setAppTheme(themeId: String) {
        context.dataStore.edit { preferences ->
            preferences[appThemeKey] = themeId
        }
    }

    suspend fun getAppTheme(): String {
        return context.dataStore.data.first()[appThemeKey] ?: "aurora"
    }
}
