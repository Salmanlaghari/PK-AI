package com.salmanlaghari.pkai.data.remote.provider

import com.salmanlaghari.pkai.data.remote.ChatCompletionRequest
import com.salmanlaghari.pkai.data.remote.ChatCompletionResponse
import com.google.gson.JsonElement
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

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
 * Cloudflare Workers AI — custom REST shape (NOT OpenAI compatible)
 * POST /accounts/{accountId}/ai/run/{modelPath}
 * ───────────────────────────────────────────────────────────────────────────── */

data class CloudflareMessage(val role: String, val content: String)

data class CloudflareRequest(
    val messages: List<CloudflareMessage>,
    val stream: Boolean = false
)

data class CloudflareResult(val response: String?)

data class CloudflareResponse(
    val result: CloudflareResult?,
    val success: Boolean = true
)

interface CloudflareWorkersApiService {
    @POST("accounts/{accountId}/ai/run/{modelPath}")
    suspend fun run(
        @Path("accountId") accountId: String,
        @Path("modelPath", encoded = true) modelPath: String,
        @Header("Authorization") authorization: String,
        @Body request: CloudflareRequest
    ): CloudflareResponse
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
