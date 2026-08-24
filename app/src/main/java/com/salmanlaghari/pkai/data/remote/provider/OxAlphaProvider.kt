package com.salmanlaghari.pkai.data.remote.provider

import com.google.gson.JsonParser
import com.salmanlaghari.pkai.data.model.ChatMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * Key-less provider backed by oxalpha.com's free chat API.
 *
 * The endpoint returns an SSE (Server-Sent Events) stream in OpenAI-compatible
 * chunked format:
 *
 *   POST https://oxalpha.com/api/chat
 *   Body: { "model": "stealth/ox-alpha", "messages": [...] }
 *   Response: `data: {"choices":[{"delta":{"content":"..."}}]}` lines + `data: [DONE]`
 *
 * Implementation notes:
 *  - Request bodies are built and responses parsed with **Gson** ([JsonParser]), not
 *    `org.json`. The JVM unit-test environment runs against android.jar stubs where
 *    stubbed `org.json` methods return null (`isReturnDefaultValues = true`), which
 *    made `JSONObject.toString()` throw "toString(...) must not be null" during CI
 *    verification. Gson is a plain-Java library and behaves identically on the JVM.
 *  - Every JSON access is null-checked; malformed or empty streams produce a
 *    meaningful [AiResponse.Error] instead of throwing.
 */
class OxAlphaProvider(
    private val okHttpClient: OkHttpClient
) : AiProvider {

    private companion object {
        const val API_URL = "https://oxalpha.com/api/chat"
        const val MODEL = "stealth/ox-alpha"
        const val DISPLAY_NAME = "Ox Alpha"
        const val MAX_HISTORY_TURNS = 20

        /** The upstream is a shared free endpoint that briefly rate-limits under load;
         *  its own error message says "wait a few seconds and retry", so we do exactly
         *  that (a small, bounded number of times) before surfacing the failure. */
        const val MAX_ATTEMPTS = 4
        const val RETRY_DELAY_MS = 3_000L

        /** Substrings in an upstream error payload that indicate a transient condition
         *  worth retrying (as opposed to a permanent request failure). */
        val TRANSIENT_MARKERS = listOf("overloaded", "rate-limited", "rate limited", "temporarily", "capacity", "try again")
    }

    /** Outcome of one request attempt against the Ox Alpha endpoint. */
    private sealed interface AttemptResult {
        data class Done(val response: AiResponse) : AttemptResult
        data object Retry : AttemptResult
    }

    override fun sendMessage(
        prompt: String,
        history: List<ChatMessage>,
        imageDataUri: String?
    ): Flow<AiResponse> = flow {
        try {
            var lastError: String? = null
            for (attempt in 1..MAX_ATTEMPTS) {
                when (val outcome = attemptRequest(prompt, history)) {
                    is AttemptResult.Done -> {
                        emit(outcome.response)
                        return@flow
                    }
                    AttemptResult.Retry -> {
                        lastError = "$DISPLAY_NAME is busy right now (attempt $attempt/$MAX_ATTEMPTS)."
                        if (attempt < MAX_ATTEMPTS) delay(RETRY_DELAY_MS)
                    }
                }
            }
            emit(AiResponse.Error(
                "${lastError ?: ""} The service is temporarily overloaded — please try again shortly."
            ))
        } catch (e: IOException) {
            emit(AiResponse.Error("$DISPLAY_NAME: Network error. Check your internet connection."))
        } catch (e: Exception) {
            emit(AiResponse.Error("$DISPLAY_NAME: ${e.localizedMessage ?: "Unknown error"}"))
        }
    }

    /** Performs one full request/response cycle against the Ox Alpha chat API. */
    private fun attemptRequest(prompt: String, history: List<ChatMessage>): AttemptResult {
        val requestBody = buildRequestBody(prompt, history)

        val request = Request.Builder()
            .url(API_URL)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .header("User-Agent", "PK-AI-Android/1.0")
            .header("Referer", "https://oxalpha.com/chat")
            .header("Origin", "https://oxalpha.com")
            .build()

        val response = okHttpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                // 428 = the endpoint's per-IP anonymous-message checkpoint (Turnstile);
                // it is sticky for the current network, so report it instead of retrying.
                if (resp.code == 428) {
                    return AttemptResult.Done(AiResponse.Error(
                        "$DISPLAY_NAME reached its free per-network message limit and needs a quick " +
                            "verification. Please try again later or use a different network."
                    ))
                }
                // Transient upstream failures are worth retrying; other client errors are not.
                return if (resp.code == 429 || resp.code in 500..599) AttemptResult.Retry
                else AttemptResult.Done(AiResponse.Error(
                    "$DISPLAY_NAME: Request failed (HTTP ${resp.code}). Please try again."
                ))
            }

            val body = resp.body ?: return AttemptResult.Retry

            val result = StringBuilder()
            var sawDataLine = false
            var transientError = false
            val reader = BufferedReader(InputStreamReader(body.byteStream()))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (!l.startsWith("data:")) continue
                val data = l.removePrefix("data:").trim()
                if (data.isEmpty() || data == "[DONE]") continue
                sawDataLine = true

                val content = extractContent(data)
                if (!content.isNullOrEmpty()) {
                    result.append(content)
                    continue
                }
                // No content chunk — check whether the event carries an API error payload.
                val apiError = extractErrorMessage(data)
                if (apiError != null && TRANSIENT_MARKERS.any { apiError.lowercase().contains(it) }) {
                    transientError = true
                }
            }
            reader.close()

            val text = result.toString().trim()
            return when {
                text.isNotEmpty() -> AttemptResult.Done(AiResponse.Success(text))
                transientError -> AttemptResult.Retry
                else -> AttemptResult.Done(AiResponse.Error(
                    "$DISPLAY_NAME returned an empty response. Please try again."
                ))
            }
        }
    }

    /**
     * Builds the JSON request body with Gson so it works identically on Android and
     * on the JVM used by unit tests (org.json is stubbed there).
     */
    private fun buildRequestBody(prompt: String, history: List<ChatMessage>): okhttp3.RequestBody {
        val messages = com.google.gson.JsonArray()
        for (msg in history.takeLast(MAX_HISTORY_TURNS)) {
            messages.add(com.google.gson.JsonObject().apply {
                addProperty("role", if (msg.isUser) "user" else "assistant")
                addProperty("content", msg.content.trim())
            })
        }
        messages.add(com.google.gson.JsonObject().apply {
            addProperty("role", "user")
            addProperty("content", prompt.trim())
        })

        val payload = com.google.gson.JsonObject().apply {
            addProperty("model", MODEL)
            add("messages", messages)
        }
        return payload.toString().toRequestBody("application/json".toMediaType())
    }

    /**
     * Null-safe extraction of the streamed text from one SSE `data:` payload.
     *
     * Returns the delta content string (possibly empty), or null when the payload is
     * not a well-formed chat completion chunk. Never throws on malformed JSON.
     */
    private fun extractContent(data: String): String? = runCatching {
        val root = JsonParser.parseString(data)
        if (!root.isJsonObject) return@runCatching null
        val choices = root.asJsonObject.get("choices") as? com.google.gson.JsonArray
            ?: return@runCatching null
        val first = choices.firstOrNull() as? com.google.gson.JsonObject ?: return@runCatching null
        val delta = first.get("delta") as? com.google.gson.JsonObject ?: return@runCatching null
        val content = delta.get("content")?.takeIf { it.isJsonPrimitive }?.asString
        content ?: ""
    }.getOrNull()

    /** Extracts a human-readable message from an SSE error event, or null if none. */
    private fun extractErrorMessage(data: String): String? = runCatching {
        val root = JsonParser.parseString(data)
        if (!root.isJsonObject) return@runCatching null
        val obj = root.asJsonObject
        when (val error = obj.get("error") ?: return@runCatching null) {
            is com.google.gson.JsonPrimitive -> error.asString
            is com.google.gson.JsonObject -> error.get("message")?.takeIf { it.isJsonPrimitive }?.asString
                ?: error.toString()
            else -> null
        }
    }.getOrNull()
}
