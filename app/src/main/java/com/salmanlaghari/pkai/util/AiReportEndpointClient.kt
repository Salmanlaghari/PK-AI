package com.salmanlaghari.pkai.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.util.concurrent.TimeUnit

internal sealed class ReportSubmissionResult {
    data object Sent : ReportSubmissionResult()
    data class Failed(val code: String) : ReportSubmissionResult()
}

/** Sends a bounded JSON report to a configured HTTPS endpoint. */
internal class AiReportEndpointClient(
    private val endpoint: String,
    private val httpClient: OkHttpClient = defaultHttpClient()
) {
    suspend fun submit(payloadJson: String): ReportSubmissionResult {
        if (!isHttpsEndpoint(endpoint)) return ReportSubmissionResult.Failed("NOT_CONFIGURED")
        if (payloadJson.isBlank() || payloadJson.length > MAX_PAYLOAD_CHARS) {
            return ReportSubmissionResult.Failed("INVALID_PAYLOAD")
        }

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("Accept", "application/json")
                    .header("Cache-Control", "no-store")
                    .post(payloadJson.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val responseText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext ReportSubmissionResult.Failed("HTTP_${response.code}")
                    }
                    parseResponse(responseText)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ReportSubmissionResult.Failed("NETWORK_ERROR")
            }
        }
    }

    private fun parseResponse(responseText: String): ReportSubmissionResult {
        val match = RESPONSE_PATTERN.matchEntire(responseText)
            ?: return ReportSubmissionResult.Failed("INVALID_RESPONSE")
        if (match.groupValues[1] == "true") return ReportSubmissionResult.Sent
        return ReportSubmissionResult.Failed(match.groupValues[3].ifBlank { "REJECTED" })
    }

    companion object {
        private const val MAX_PAYLOAD_CHARS = 8_192
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val RESPONSE_PATTERN = Regex(
            """^\s*\{\s*"ok"\s*:\s*(true|false)(?:\s*,\s*"(code|duplicate)"\s*:\s*(?:"([A-Z_]+)"|true|false))?\s*}\s*$"""
        )

        internal fun isHttpsEndpoint(value: String): Boolean = runCatching {
            val uri = URI(value)
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.equals("script.google.com", ignoreCase = true) &&
                uri.rawUserInfo == null &&
                (uri.port == -1 || uri.port == 443) &&
                uri.query == null && uri.fragment == null &&
                uri.path.startsWith("/macros/s/") && uri.path.endsWith("/exec")
        }.getOrDefault(false)

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            // Apps Script Content Service redirects JSON responses to a one-time
            // googleusercontent URL. OkHttp follows HTTPS redirects by default.
            .followRedirects(true)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
