package com.salmanlaghari.pkai.data.remote.provider

import com.salmanlaghari.pkai.data.remote.ChatCompletionRequest
import com.salmanlaghari.pkai.data.remote.ChatCompletionResponse
import com.google.gson.JsonElement
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/* ─────────────────────────────────────────────────────────────────────────────
 * OpenAI-compatible providers (Groq, LLM7.io, Mistral, Cerebras, Hugging Face)
 * All of these expose the standard /chat/completions schema.
 * ───────────────────────────────────────────────────────────────────────────── */

interface OpenAiCompatibleApiService {
    @POST("chat/completions")
    suspend fun generateChatResponse(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse
}

/* ─────────────────────────────────────────────────────────────────────────────
 * Cohere — v2 chat API custom shape (NOT OpenAI compatible)
 * POST /v2/chat
 * ───────────────────────────────────────────────────────────────────────────── */

data class CohereMessage(val role: String, val content: String)

data class CohereRequest(
    val model: String,
    val messages: List<CohereMessage>,
    val stream: Boolean = false
)

data class CohereMessageResponse(
    val role: String? = null,
    // Cohere returns `content` either as a plain string or a list of {type,text} blocks.
    val content: JsonElement? = null
)

data class CohereResponse(val message: CohereMessageResponse? = null)

interface CohereApiService {
    @POST("chat")
    suspend fun chat(
        @Header("Authorization") authorization: String,
        @Body request: CohereRequest
    ): CohereResponse
}

/* ─────────────────────────────────────────────────────────────────────────────
 * Pollinations AI — key-less free LLM used by the Home "Free AI" tab.
 *
 * Anonymous GET https://text.pollinations.ai/{prompt} returns the completion as
 * plain text (no JSON envelope, no Authorization header), so the response is read
 * as a raw ResponseBody rather than going through the Gson converter.
 * ───────────────────────────────────────────────────────────────────────────── */

interface PollinationsApiService {
    @GET("{prompt}")
    suspend fun generate(
        @Path("prompt") prompt: String,
        @Query("model") model: String,
        // `private=true` asks Pollinations to keep the completion out of its public feed.
        @Query("private") isPrivate: Boolean
    ): ResponseBody
}

