package com.salmanlaghari.pkai.data.repository

sealed interface ImageGenerationResult {
    data class Success(val bytes: ByteArray) : ImageGenerationResult
    data class RateLimited(val retryAfterMs: Long?, val message: String) : ImageGenerationResult
    data class QuotaExceeded(val message: String) : ImageGenerationResult
    data class AuthenticationFailed(val message: String) : ImageGenerationResult
    data class Unavailable(val message: String) : ImageGenerationResult
    data class InvalidRequest(val message: String) : ImageGenerationResult
    data class NetworkError(val message: String, val cause: Throwable? = null) : ImageGenerationResult
    /**
     * The prompt was refused by the client-side content-safety filter
     * ([com.salmanlaghari.pkai.util.ContentSafetyFilter]) or flagged by the
     * image service's own safety filter. No image was generated and — for the
     * client-side case — no network call was made at all.
     */
    data class Blocked(val message: String) : ImageGenerationResult
}

interface ImageGenerationProvider {
    suspend fun generateImage(prompt: String, width: Int = 768, height: Int = 768): ImageGenerationResult
}
