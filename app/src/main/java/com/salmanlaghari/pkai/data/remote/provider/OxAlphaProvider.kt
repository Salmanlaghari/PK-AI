package com.salmanlaghari.pkai.data.remote.provider

import com.google.gson.JsonParser
import com.salmanlaghari.pkai.data.model.ChatMessage
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
    }

    override fun sendMessage(
        prompt: String,
        history: List<ChatMessage>,
        imageDataUri: String?
    ): Flow<AiResponse> = flow {
        try {
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
            if (!response.isSuccessful) {
                emit(AiResponse.Error("$DISPLAY_NAME: Request failed (HTTP ${response.code}). Please try again."))
                return@flow
            }

            val body = response.body ?: run {
                emit(AiResponse.Error("$DISPLAY_NAME: Empty response. Please try again."))
                return@flow
            }

            val result = StringBuilder()
            var sawDataLine = false
            var errorMessage: String? = null
            val reader = BufferedReader(InputStreamReader(body.byteStream()))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (!l.startsWith("data:")) continue
                val data = l.removePrefix("data:").trim()
                if (data.isEmpty() || data == "[DONE]") continue
                sawDataLine = true

                val content = extractContent(data)
                if (content != null) {
                    result.append(content)
                    continue
                }
                // No content chunk — check whether the event carries an API error payload.
                val apiError = extractErrorMessage(data)
                if (apiError != null && errorMessage == null) {
                    errorMessage = apiError
                }
            }
            reader.close()
            body.close()

            if (errorMessage != null) {
                emit(AiResponse.Error("$DISPLAY_NAME: $errorMessage"))
                return@flow
            }

            val text = result.toString().trim()
            if (!sawDataLine || text.isEmpty()) {
                emit(AiResponse.Error("$DISPLAY_NAME returned an empty response. Please try again."))
            } else {
                emit(AiResponse.Success(text))
            }
        } catch (e: IOException) {
            emit(AiResponse.Error("$DISPLAY_NAME: Network error. Check your internet connection."))
        } catch (e: Exception) {
            emit(AiResponse.Error("$DISPLAY_NAME: ${e.localizedMessage ?: "Unknown error"}"))
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
