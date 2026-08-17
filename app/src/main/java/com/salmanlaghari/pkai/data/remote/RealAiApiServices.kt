package com.salmanlaghari.pkai.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

// --- OpenAI-Compatible APIs ---

interface OpenRouterApiService {
    @POST("chat/completions")
    suspend fun generateChatResponse(
        @Header("Authorization") authorization: String,
        @Header("HTTP-Referer") referer: String = "https://pkai.salmanlaghari.com",
        @Header("X-Title") title: String = "PK AI",
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse
}

interface TogetherApiService {
    @POST("chat/completions")
    suspend fun generateChatResponse(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse
}

interface CerebrasApiService {
    @POST("chat/completions")
    suspend fun generateChatResponse(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse
}

interface SambaNovaApiService {
    @POST("chat/completions")
    suspend fun generateChatResponse(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse
}

// --- Anthropic (Claude) API ---

data class AnthropicMessage(val role: String, val content: String)

data class AnthropicRequest(
    val model: String = "claude-3-haiku-20240307",
    val max_tokens: Int = 256,
    val messages: List<AnthropicMessage>
)

data class AnthropicContentBlock(val type: String?, val text: String?)

data class AnthropicResponse(val content: List<AnthropicContentBlock>?)

interface AnthropicApiService {
    @POST("v1/messages")
    suspend fun createMessage(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") anthropicVersion: String = "2023-06-01",
        @Header("content-type") contentType: String = "application/json",
        @Body request: AnthropicRequest
    ): AnthropicResponse
}
