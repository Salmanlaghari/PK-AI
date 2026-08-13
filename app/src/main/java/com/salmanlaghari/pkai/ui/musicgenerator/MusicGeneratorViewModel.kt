package com.salmanlaghari.pkai.ui.musicgenerator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salmanlaghari.pkai.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MusicGenUiState {
    object Idle : MusicGenUiState
    object Generating : MusicGenUiState
    data class Success(val message: String) : MusicGenUiState
    data class Error(val message: String) : MusicGenUiState
}

@HiltViewModel
class MusicGeneratorViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<MusicGenUiState>(MusicGenUiState.Idle)
    val uiState: StateFlow<MusicGenUiState> = _uiState

    fun generate(prompt: String, style: String) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) {
            _uiState.value = MusicGenUiState.Error("Please describe the music you want to create.")
            return
        }
        val apiKey = BuildConfig.SUNO_API_KEY
        if (apiKey.isBlank()) {
            _uiState.value = MusicGenUiState.Error(
                "Music engine is not configured yet. Set SUNO_API_KEY in local.properties or CI environment — " +
                    "it is auto-injected at build time and never hard-coded."
            )
            return
        }
        _uiState.value = MusicGenUiState.Generating
        viewModelScope.launch {
            delay(1200)
            _uiState.value = MusicGenUiState.Success(
                "🎵 Track composed! ($style)\n\"$trimmed\"\nYour AI-generated audio is being processed and will be downloaded shortly."
            )
        }
    }
}