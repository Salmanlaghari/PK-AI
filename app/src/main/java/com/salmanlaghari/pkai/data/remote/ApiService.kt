package com.salmanlaghari.pkai.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class ChatMessageDto(
    val role: String,
    // Either a plain [String] (text) or a [List] of multimodal parts (vision input).
    val content: Any?
)

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>
)

data class ChatChoiceDto(
    val message: ChatMessageDto
)

data class ChatCompletionResponse(
    val choices: List<ChatChoiceDto>
)

interface ApiService {
    @GET("status")
    suspend fun getApiStatus(): Map<String, String>

    @POST("v1/chat/completions")
    suspend fun generateChatResponse(@Body request: ChatCompletionRequest): ChatCompletionResponse
}

interface PublicFreeApiService {
    @GET("https://catfact.ninja/fact")
    suspend fun getFreeFact(): Map<String, String>

    @GET("https://api.adviceslip.com/advice")
    suspend fun getFreeAdvice(): Map<String, Any>
}
