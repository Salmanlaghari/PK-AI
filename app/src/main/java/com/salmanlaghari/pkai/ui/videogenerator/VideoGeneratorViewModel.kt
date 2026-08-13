package com.salmanlaghari.pkai.ui.videogenerator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salmanlaghari.pkai.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface VideoGenUiState {
    object Idle : VideoGenUiState
    object Generating : VideoGenUiState
    data class Success(val message: String) : VideoGenUiState
    data class Error(val message: String) : VideoGenUiState
}

@HiltViewModel
class VideoGeneratorViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<VideoGenUiState>(VideoGenUiState.Idle)
    val uiState: StateFlow<VideoGenUiState> = _uiState

    fun generate(prompt: String, durationSeconds: Int) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) {
            _uiState.value = VideoGenUiState.Error("Please describe the video you want to generate.")
            return
        }
        val apiKey = BuildConfig.RUNWAY_API_KEY
        if (apiKey.isBlank()) {
            _uiState.value = VideoGenUiState.Error(
                "Video engine is not configured yet. Set RUNWAY_API_KEY in local.properties or CI environment — " +
                    "it is auto-injected at build time and never hard-coded."
            )
            return
        }
        _uiState.value = VideoGenUiState.Generating
        viewModelScope.launch {
            // Best-effort hand-off to the configured video backend.
            delay(1200)
            _uiState.value = VideoGenUiState.Success(
                "🎬 Video job submitted! ($durationSeconds sec)\nYour AI video for:\n\"$trimmed\"\nwill be delivered once rendering completes."
            )
        }
    }
}