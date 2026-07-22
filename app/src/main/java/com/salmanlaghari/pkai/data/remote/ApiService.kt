package com.salmanlaghari.pkai.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class ChatMessageDto(
    val role: String,
    val content: String
)

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>
)

data class ChatChoiceDto(
    val message: ChatMessageDto
)

data class ChatCompletionResponse(
    val choices: List<ChatChoiceDto>?
)

interface ApiService {
    @GET("status")
    suspend fun getApiStatus(): Map<String, String>

    @POST("v1/chat/completions")
    suspend fun generateChatResponse(@Body request: ChatCompletionRequest): ChatCompletionResponse
}
