package com.salmanlaghari.pkai.data.repository

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import com.salmanlaghari.pkai.BuildConfig
import com.salmanlaghari.pkai.util.ContentSafetyFilter
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

@Singleton
class PollinationsImageRepository @Inject constructor() : ImageGenerationProvider {
    companion object {
        const val DEFAULT_WIDTH = 768
        const val DEFAULT_HEIGHT = 768
        const val MAX_ATTEMPTS = 4
        const val INITIAL_BACKOFF_MS = 2000L
        const val MAX_BACKOFF_MS = 16000L
        private const val TIMEOUT_SECONDS = 40L
        private const val TAG = "PollinationsImageRepo"
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    fun buildImageUrl(prompt: String, width: Int = DEFAULT_WIDTH, height: Int = DEFAULT_HEIGHT, model: String = "flux"): String {
        val encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
        val apiKey = BuildConfig.POLLINATIONS_API_KEY
        val maskedKey = if (apiKey.isNotBlank()) apiKey.take(4) + "****" else "<empty>"
        val tokenParam = if (apiKey.isNotBlank()) "&token=$apiKey" else ""
        // safe=true: ask Pollinations to apply strict NSFW filtering server-side
        // (it throws an error when NSFW content is detected — handled below).
        // This is the second layer; ContentSafetyFilter runs client-side first.
        val url = if (model.isNotBlank()) {
            "https://image.pollinations.ai/prompt/$encodedPrompt?width=$width&height=$height&nologo=true&model=$model&enhance=true&safe=true$tokenParam"
        } else {
            "https://image.pollinations.ai/prompt/$encodedPrompt?width=$width&height=$height&nologo=true&safe=true$tokenParam"
        }
        val maskedUrl = url.replace(apiKey, maskedKey)
        Log.d(TAG, "Built image URL (model=$model, key_present=${apiKey.isNotBlank()}): $maskedUrl")
        return url
    }

    override suspend fun generateImage(prompt: String, width: Int, height: Int): ImageGenerationResult = withContext(Dispatchers.IO) {
        // Play-policy choke point: STRICT client-side NSFW filter runs FIRST,
        // before any network call. A blocked prompt never reaches the service.
        val blockedReason = ContentSafetyFilter.blockedReason(prompt)
        if (blockedReason != null) {
            Log.w(TAG, "Prompt blocked by content safety filter ($blockedReason)")
            return@withContext ImageGenerationResult.Blocked(
                "This prompt isn't allowed. Please try something else."
            )
        }

        var attempt = 0
        var backoffMs = INITIAL_BACKOFF_MS
        var lastResult: ImageGenerationResult? = null

        while (attempt < MAX_ATTEMPTS) {
            attempt++
            try {
                val url = buildImageUrl(prompt, width, height, "flux")
                val request = Request.Builder().url(url).get().build()
                val apiKey = BuildConfig.POLLINATIONS_API_KEY
                val maskedKey = if (apiKey.isNotBlank()) apiKey.take(4) + "****" else "<empty>"
                val maskedUrl = url.replace(apiKey, maskedKey)
                Log.d(TAG, "Attempt $attempt: GET $maskedUrl")
                val response = okHttpClient.newCall(request).execute()
                response.use { resp ->
                    val statusCode = resp.code
                    val bodyBytes = resp.body?.bytes()
                    val bodySnippet = bodyBytes?.toString(Charsets.UTF_8)?.take(200) ?: "<empty body>"
                    Log.d(TAG, "Attempt $attempt: HTTP $statusCode, body: $bodySnippet")

                    // With safe=true the service throws an error when it detects
                    // NSFW content — it can surface as a non-200 or as a 200
                    // with a non-image error payload. Map it to a friendly
                    // "flagged" message (no retry: re-asking won't help) instead
                    // of a generic failure or a crash.
                    val looksLikeImage = bodyBytes != null && bodyBytes.isNotEmpty() && isValidImageBytes(bodyBytes)
                    if (!looksLikeImage && isSafetyFlag(bodySnippet)) {
                        return@withContext ImageGenerationResult.Blocked(
                            "The image service flagged this prompt as unsafe. Please try a different prompt."
                        )
                    }

                    when (statusCode) {
                        200 -> {
                            if (bodyBytes != null && bodyBytes.isNotEmpty() && isValidImageBytes(bodyBytes)) {
                                return@withContext ImageGenerationResult.Success(bodyBytes)
                            }
                            lastResult = ImageGenerationResult.Unavailable("The image service returned an empty or invalid image.")
                        }
                        401, 403 -> {
                            lastResult = ImageGenerationResult.AuthenticationFailed("Authentication failed (HTTP $statusCode).")
                        }
                        402 -> {
                            lastResult = ImageGenerationResult.QuotaExceeded("Quota exceeded (HTTP 402).")
                        }
                        429 -> {
                            val retryAfter = resp.header("Retry-After")?.toLongOrNull()
                            if (retryAfter != null && retryAfter > 0) {
                                 backoffMs = minOf(retryAfter * 1000, MAX_BACKOFF_MS)
                            }
                            lastResult = ImageGenerationResult.RateLimited(retryAfter, "Rate limited (HTTP 429).")
                        }
                        in 500..599 -> {
                            lastResult = ImageGenerationResult.Unavailable("Service unavailable (HTTP $statusCode).")
                        }
                        else -> {
                            lastResult = ImageGenerationResult.InvalidRequest("Invalid request (HTTP $statusCode).")
                        }
                    }
                }
            } catch (e: SocketTimeoutException) {
                lastResult = ImageGenerationResult.NetworkError("Request timed out: ${e.message}", e)
            } catch (e: UnknownHostException) {
                lastResult = ImageGenerationResult.NetworkError("Network unreachable: ${e.message}", e)
            } catch (e: IOException) {
                lastResult = ImageGenerationResult.NetworkError("I/O error: ${e.message}", e)
            } catch (e: Exception) {
                return@withContext ImageGenerationResult.NetworkError("Unexpected error: ${e.message}", e)
            }

            if (attempt < MAX_ATTEMPTS) {
                val jitter = (Math.random() * backoffMs / 2).toLong()
                kotlinx.coroutines.delay(backoffMs + jitter)
                backoffMs = minOf(backoffMs * 2, MAX_BACKOFF_MS)
            }
        }
        return@withContext lastResult ?: ImageGenerationResult.Unavailable("Image generation failed after $MAX_ATTEMPTS attempts.")
    }

    /**
     * True when a non-image response body looks like the image service's
     * safety filter rejecting the prompt (used with `safe=true`). Matched
     * loosely on purpose — the exact error shape is not contractual.
     */
    private fun isSafetyFlag(bodySnippet: String): Boolean {
        val b = bodySnippet.lowercase()
        return b.contains("nsfw") ||
            b.contains("not safe") ||
            b.contains("unsafe") ||
            b.contains("inappropriate") ||
            b.contains("content policy") ||
            b.contains("policy violation") ||
            b.contains("safety filter")
    }

    private fun isValidImageBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return true
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) return true
        if (bytes.size >= 12 &&
            bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() && bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()) return true
        return false
    }
}
