package com.salmanlaghari.pkai.data.repository

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import com.salmanlaghari.pkai.BuildConfig
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
        val url = if (model.isNotBlank()) {
            "https://image.pollinations.ai/prompt/$encodedPrompt?width=$width&height=$height&nologo=true&model=$model&enhance=true$tokenParam"
        } else {
            "https://image.pollinations.ai/prompt/$encodedPrompt?width=$width&height=$height&nologo=true$tokenParam"
        }
        val maskedUrl = url.replace(apiKey, maskedKey)
        Log.d(TAG, "Built image URL (model=$model, key_present=${apiKey.isNotBlank()}): $maskedUrl")
        return url
    }

    override suspend fun generateImage(prompt: String, width: Int, height: Int): ImageGenerationResult = withContext(Dispatchers.IO) {
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
