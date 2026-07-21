package com.salmanlaghari.pkai.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

// --- Gemini API Models ---
data class GeminiPart(val text: String)
data class GeminiContent(val parts: List<GeminiPart>)
data class GeminiRequest(val contents: List<GeminiContent>)

data class GeminiCandidatePart(val text: String?)
data class GeminiCandidateContent(val parts: List<GeminiCandidatePart>?)
data class GeminiCandidate(val content: GeminiCandidateContent?)
data class GeminiResponse(val candidates: List<GeminiCandidate>?)

interface GeminiApiService {
    @POST("v1beta/models/gemini-1.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse

    @retrofit2.http.Streaming
    @POST("v1beta/models/gemini-1.5-flash:streamGenerateContent")
    suspend fun streamGenerateContent(
        @Query("key") apiKey: String,
        @Query("alt") alt: String = "sse",
        @Body request: GeminiRequest
    ): okhttp3.ResponseBody
}

// --- OpenAI-Compatible APIs ---

interface OpenRouterApiService {
    @POST("chat/completions")
    suspend fun generateChatResponse(
        @Header("Authorization") authorization: String,
        @Header("HTTP-Referer") referer: String = "https://pkai.salmanlaghari.com",
        @Header("X-Title") title: String = "PK AI",
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse

    @retrofit2.http.Streaming
    @POST("chat/completions")
    suspend fun streamChatResponse(
        @Header("Authorization") authorization: String,
        @Header("HTTP-Referer") referer: String = "https://pkai.salmanlaghari.com",
        @Header("X-Title") title: String = "PK AI",
        @Body request: ChatCompletionRequest
    ): okhttp3.ResponseBody
}

// --- Cohere API Models ---
data class CohereChatRequest(
    val message: String,
    val model: String = "command-r-plus"
)

data class CohereChatResponse(
    val text: String?
)

interface CohereApiService {
    @POST("v1/chat")
    suspend fun generateChatResponse(
        @Header("Authorization") authorization: String,
        @Body request: CohereChatRequest
    ): CohereChatResponse
}

interface GroqApiService {
    @POST("chat/completions")
    suspend fun generateChatResponse(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse

    @retrofit2.http.Streaming
    @POST("chat/completions")
    suspend fun streamChatResponse(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): okhttp3.ResponseBody
}

interface TogetherApiService {
    @POST("chat/completions")
    suspend fun generateChatResponse(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse

    @retrofit2.http.Streaming
    @POST("chat/completions")
    suspend fun streamChatResponse(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): okhttp3.ResponseBody
}

interface OpenAiApiService {
    @POST("chat/completions")
    suspend fun generateChatResponse(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse

    @retrofit2.http.Streaming
    @POST("chat/completions")
    suspend fun streamChatResponse(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): okhttp3.ResponseBody
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
