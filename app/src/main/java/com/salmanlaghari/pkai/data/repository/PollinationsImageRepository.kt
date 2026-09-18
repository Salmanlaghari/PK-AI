package com.salmanlaghari.pkai.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for the free, key-less Pollinations.ai image endpoint.
 *
 * Endpoint: `https://image.pollinations.ai/prompt/{urlEncodedPrompt}`
 *   - Optional query params: `width`, `height`, `seed`, `model`, `nologo`, `enhance`.
 *   - No API key / auth headers are required, so nothing here is wired into
 *     [com.salmanlaghari.pkai.BuildConfig] or CI secrets.
 *   - The response is raw image bytes (PNG/JPEG), not JSON.
 *
 * Results are returned as a sealed [ImageGenerationResult] so callers can render the
 * Bitmap inline or surface a clear fallback message on failure.
 */
@Singleton
class PollinationsImageRepository @Inject constructor(
    private val okHttpClient: OkHttpClient
) {

    companion object {
        private const val BASE_URL = "https://image.pollinations.ai/prompt/"

        /** Default Flux model used when the caller does not specify one. */
        const val DEFAULT_MODEL = "flux"

        /** Default render resolution for the free endpoint (fast + cheap). */
        const val DEFAULT_WIDTH = 768
        const val DEFAULT_HEIGHT = 768

        private const val TAG = "PollinationsImageRepository"
    }

    /**
     * Builds the full request URL for a prompt, URL-encoding it so multi-word prompts,
     * punctuation and commas survive the path segment intact.
     *
     * @param prompt the raw text prompt.
     * @param width  optional image width (px).
     * @param height optional image height (px).
     * @param seed   optional deterministic seed; null = random per request.
     * @param model  optional model id; null falls back to [DEFAULT_MODEL].
     */
    fun buildImageUrl(
        prompt: String,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT,
        seed: Int? = null,
        model: String? = null
    ): String {
        val encodedPrompt = java.net.URLEncoder.encode(prompt.trim(), "UTF-8")
        val sb = StringBuilder(BASE_URL).append(encodedPrompt)
            .append("?width=").append(width)
            .append("&height=").append(height)
            .append("&nologo=true")
            .append("&enhance=true")
        if (model != null) sb.append("&model=").append(java.net.URLEncoder.encode(model, "UTF-8"))
        if (seed != null) sb.append("&seed=").append(seed)
        return sb.toString()
    }

    /**
     * Downloads the generated image and decodes it into a [Bitmap].
     *
     * @return [ImageGenerationResult.Success] with the decoded Bitmap, or a typed
     *   [ImageGenerationResult.Error] describing what went wrong (network failure,
     *   timeout, non-200 response, empty body, decode failure).
     */
    fun generateImage(prompt: String): ImageGenerationResult {
        if (prompt.isBlank()) {
            return ImageGenerationResult.Error("Prompt is empty.")
        }

        val url = buildImageUrl(prompt)
        val request = Request.Builder().url(url).get().build()

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return ImageGenerationResult.Error(
                        "Image request failed (HTTP ${response.code})."
                    )
                }
                val bytes = response.body?.bytes()
                if (bytes == null || bytes.isEmpty()) {
                    return ImageGenerationResult.Error(
                        "The image service returned an empty image."
                    )
                }
                val bitmap = bitmapFromBytes(bytes)
                    ?: return ImageGenerationResult.Error("Could not decode the image bytes.")
                ImageGenerationResult.Success(bitmap)
            }
        } catch (e: Exception) {
            ImageGenerationResult.Error(e.localizedMessage ?: "Network error")
        }
    }

    private fun bitmapFromBytes(bytes: ByteArray): Bitmap? =
        DecodeResult(bytes).decode()

    private class DecodeResult(val bytes: ByteArray) {
        fun decode(): Bitmap? = DecodeFactory.decode(bytes)
    }

    private object DecodeFactory {
        fun decode(bytes: ByteArray): Bitmap? = DecodeResult(bytes).decode()
    }
}