package com.salmanlaghari.pkai.ui.aigenerator

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salmanlaghari.pkai.BuildConfig
import com.salmanlaghari.pkai.data.remote.OpenAiImageApiService
import com.salmanlaghari.pkai.data.remote.OpenAiImageRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface ImageGenUiState {
    object Idle : ImageGenUiState
    object Loading : ImageGenUiState
    data class Success(val bitmap: Bitmap, val revisedPrompt: String?) : ImageGenUiState
    data class Error(val message: String) : ImageGenUiState
}

@HiltViewModel
class ImageGeneratorViewModel @Inject constructor(
    private val imageApiService: OpenAiImageApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImageGenUiState>(ImageGenUiState.Idle)
    val uiState: StateFlow<ImageGenUiState> = _uiState

    fun generate(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) {
            _uiState.value = ImageGenUiState.Error("Please describe the image you want to create.")
            return
        }
        val apiKey = BuildConfig.OPENAI_API_KEY
        if (apiKey.isBlank()) {
            _uiState.value = ImageGenUiState.Error(
                "No API key found. Set OPENAI_API_KEY in local.properties or CI environment — " +
                    "it is auto-injected at build time and never hard-coded."
            )
            return
        }
        _uiState.value = ImageGenUiState.Loading
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val response = imageApiService.generateImage(
                        authorization = "Bearer $apiKey",
                        request = OpenAiImageRequest(prompt = trimmed)
                    )
                    val imageData = response?.data?.firstOrNull()
                        ?: throw IllegalStateException("The image service returned no result. Check your prompt or quota.")
                    val base64 = imageData.b64_json
                        ?: throw IllegalStateException("No image payload received. Check your API plan.")
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: throw IllegalStateException("Could not decode the generated image.")
                    bitmap to imageData.revised_prompt
                }
            }
            _uiState.value = result.fold(
                onSuccess = { (bitmap, revised) -> ImageGenUiState.Success(bitmap, revised) },
                onFailure = { e -> ImageGenUiState.Error(e.localizedMessage ?: "Image generation failed.") }
            )
        }
    }
}