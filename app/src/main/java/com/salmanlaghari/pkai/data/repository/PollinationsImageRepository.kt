package com.salmanlaghari.pkai.data.repository

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Inject
import javax.inject.Singleton
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class PollinationsImageRepository @Inject constructor() {
    companion object {
        const val DEFAULT_WIDTH = 768
        const val DEFAULT_HEIGHT = 768
        const val MAX_ATTEMPTS = 4
        const val INITIAL_BACKOFF_MS = 2000L
        private const val TIMEOUT_SECONDS = 40L
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        })
        .connectTimeout(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    fun buildImageUrl(prompt: String, width: Int = DEFAULT_WIDTH, height: Int = DEFAULT_HEIGHT, model: String = "flux"): String {
        val encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
        return if (model.isNotBlank()) {
            "https://image.pollinations.ai/prompt/$encodedPrompt?width=$width&height=$height&nologo=true&model=$model&enhance=true"
        } else {
            "https://image.pollinations.ai/prompt/$encodedPrompt?width=$width&height=$height&nologo=true"
        }
    }

    suspend fun generateImage(prompt: String): ByteArray = withContext(Dispatchers.IO) {
        var attempt = 0
        var backoffMs = INITIAL_BACKOFF_MS
        var lastError: String? = null
        val modelsToTry = listOf("flux", "turbo", "")

        while (attempt < MAX_ATTEMPTS) {
            val currentModel = modelsToTry.getOrElse(attempt) { "turbo" }
            attempt++
            try {
                val url = buildImageUrl(prompt, model = currentModel)
                val request = Request.Builder().url(url).get().build()
                val response = okHttpClient.newCall(request).execute()
                response.use { resp ->
                    if (resp.isSuccessful) {
                        val bytes = resp.body?.bytes()
                        if (bytes != null && bytes.isNotEmpty()) return@withContext bytes
                        lastError = "The image service returned an empty image."
                    } else {
                        lastError = "Image request failed (HTTP ${resp.code})."
                    }
                }
            } catch (e: Exception) {
                val errorType = when (e) {
                    is SocketTimeoutException -> "SocketTimeoutException"
                    is UnknownHostException -> "UnknownHostException"
                    else -> e.javaClass.simpleName
                }
                lastError = "$errorType: ${e.localizedMessage ?: e.message ?: "Unknown error"}"
            }
            if (attempt < MAX_ATTEMPTS) {
                kotlinx.coroutines.delay(backoffMs)
                backoffMs *= 2
            }
        }
        throw IllegalStateException("Image generation didn't succeed after $MAX_ATTEMPTS tries ($lastError)")
    }
}
