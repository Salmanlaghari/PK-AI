package com.salmanlaghari.pkai.data.remote.provider

import com.salmanlaghari.pkai.data.model.AiModel
import com.salmanlaghari.pkai.data.remote.ApiService
import com.salmanlaghari.pkai.data.remote.ChatChoiceDto
import com.salmanlaghari.pkai.data.remote.ChatCompletionRequest
import com.salmanlaghari.pkai.data.remote.ChatCompletionResponse
import com.salmanlaghari.pkai.data.remote.ChatMessageDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever

class AiProviderFactoryTest {

    private lateinit var mockApiService: ApiService
    private lateinit var factory: AiProviderFactory

    @Before
    fun setUp() {
        mockApiService = mock(ApiService::class.java)
        factory = AiProviderFactory(mockApiService)
    }

    @Test
    fun `getProvider returns PlaceholderAiProvider when usePlaceholder is true`() = runTest {
        val provider = factory.getProvider(AiModel.GEMINI, usePlaceholder = true)
        assertTrue(provider is PlaceholderAiProvider)

        val response = provider.generateResponse("test prompt")
        assertTrue(response.contains("Gemini"))
    }

    @Test
    fun `getProvider returns NetworkAiProvider when usePlaceholder is false`() = runTest {
        val provider = factory.getProvider(AiModel.GEMINI, usePlaceholder = false)
        assertTrue(provider is NetworkAiProvider)
    }

    @Test
    fun `NetworkAiProvider calls generateChatResponse on ApiService successfully`() = runTest {
        // Given
        val provider = factory.getProvider(AiModel.GEMINI, usePlaceholder = false)
        val expectedResponse = ChatCompletionResponse(
            choices = listOf(ChatChoiceDto(message = ChatMessageDto(role = "assistant", content = "Gemini raw response")))
        )

        val request = ChatCompletionRequest(
            model = "gemini",
            messages = listOf(ChatMessageDto(role = "user", content = "What is PK AI?"))
        )
        whenever(mockApiService.generateChatResponse(request)).thenReturn(expectedResponse)

        // When
        val response = provider.generateResponse("What is PK AI?")

        // Then
        assertEquals("Gemini raw response", response)
    }

    @Test
    fun `NetworkAiProvider handles ApiService failure gracefully`() = runTest {
        // Given
        val provider = factory.getProvider(AiModel.GEMINI, usePlaceholder = false)
        val request = ChatCompletionRequest(
            model = "gemini",
            messages = listOf(ChatMessageDto(role = "user", content = "What is PK AI?"))
        )
        whenever(mockApiService.generateChatResponse(request)).thenThrow(RuntimeException("Network Timeout"))

        // When
        val response = provider.generateResponse("What is PK AI?")

        // Then
        assertTrue(response.contains("Error: Network Timeout"))
    }
}
