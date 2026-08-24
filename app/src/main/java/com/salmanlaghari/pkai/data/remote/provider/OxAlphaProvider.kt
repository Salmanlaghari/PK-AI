package com.salmanlaghari.pkai.data.remote.provider

import com.salmanlaghari.pkai.data.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * Key-less provider backed by oxalpha.com's free chat API.
 *
 * The endpoint returns an SSE (Server-Sent Events) stream in OpenAI-compatible
 * chunked format. No API key, no login, no billing — completely free.
 *
 * API details discovered from https://oxalpha.com/chat:
 *   POST https://oxalpha.com/api/chat
 *   Body: { "model": "stealth/ox-alpha", "messages": [...] }
 *   Response: SSE stream with `data: {"choices":[{"delta":{"content":"..."}}]}` lines
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
            val messages = buildMessageArray(prompt, history)
            val requestBody = JSONObject().apply {
                put("model", MODEL)
                put("messages", messages)
            }.toString().toRequestBody("application/json".toMediaType())

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
            val reader = BufferedReader(InputStreamReader(body.byteStream()))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (!l.startsWith("data: ")) continue
                val data = l.removePrefix("data: ").trim()
                if (data == "[DONE]") continue
                if (data.isEmpty()) continue

                try {
                    val json = JSONObject(data)
                    // Check for error events
                    if (json.has("error")) {
                        val error = json.getJSONObject("error")
                        val errorMsg = error.optString("error", "Unknown error")
                        emit(AiResponse.Error("$DISPLAY_NAME: $errorMsg"))
                        return@flow
                    }
                    val choices = json.optJSONArray("choices") ?: continue
                    if (choices.length() == 0) continue
                    val delta = choices.getJSONObject(0).optJSONObject("delta") ?: continue
                    val content = delta.optString("content", "")
                    if (content.isNotEmpty()) {
                        result.append(content)
                    }
                } catch (_: Exception) {
                    // Skip malformed JSON lines
                }
            }
            reader.close()
            body.close()

            val text = result.toString().trim()
            if (text.isEmpty()) {
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
     * Builds the messages JSONArray from prompt + recent conversation history.
     */
    private fun buildMessageArray(prompt: String, history: List<ChatMessage>): JSONArray {
        val messages = JSONArray()
        val recentHistory = history.takeLast(MAX_HISTORY_TURNS)

        for (msg in recentHistory) {
            messages.put(JSONObject().apply {
                put("role", if (msg.isUser) "user" else "assistant")
                put("content", msg.content.trim())
            })
        }

        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", prompt.trim())
        })

        return messages
    }
}
