package com.salmanlaghari.pkai.ui.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import com.salmanlaghari.pkai.data.local.secure.GeminiKeyStore
import com.salmanlaghari.pkai.data.model.LlmProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val geminiKeyStore: GeminiKeyStore
) : ViewModel() {

    val isDarkMode = preferencesManager.isDarkMode.asLiveData()
    val appLanguage = preferencesManager.appLanguage.asLiveData()
    val isNotificationsEnabled = preferencesManager.isNotificationsEnabled.asLiveData()
    val appTheme = preferencesManager.appTheme.asLiveData()

    /** The catalogue of free LLM providers shown in the Settings → AI section. */
    val providers: List<LlmProvider> = LlmProvider.ALL

    /** Currently selected provider id (defaults to the first provider). */
    val selectedProviderId = preferencesManager.selectedProviderId.asLiveData()

    /** True when the user has saved their own Gemini API key (BYOK). */
    private val _hasGeminiKey = MutableLiveData<Boolean>()
    val hasGeminiKey: LiveData<Boolean> = _hasGeminiKey

    init {
        refreshGeminiKeyState()
    }

    private fun refreshGeminiKeyState() {
        viewModelScope.launch {
            val has = withContext(Dispatchers.IO) { geminiKeyStore.hasApiKey() }
            _hasGeminiKey.value = has
        }
    }

    /** Saves the user's own Gemini API key (from their Google AI Studio). The value is never echoed back. */
    fun saveGeminiKey(apiKey: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { geminiKeyStore.saveApiKey(apiKey) }
            refreshGeminiKeyState()
        }
    }

    /** Removes the user's own Gemini API key; the app falls back to the shared build key. */
    fun clearGeminiKey() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { geminiKeyStore.clearApiKey() }
            refreshGeminiKeyState()
        }
    }

    fun selectProvider(providerId: String) {
        viewModelScope.launch {
            preferencesManager.setSelectedProviderId(providerId)
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setDarkMode(enabled)
        }
    }

    fun setAppLanguage(languageCode: String) {
        viewModelScope.launch {
            preferencesManager.setAppLanguage(languageCode)
        }
    }

    fun setAppTheme(themeId: String) {
        viewModelScope.launch {
            preferencesManager.setAppTheme(themeId)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setNotificationsEnabled(enabled)
        }
    }
}
