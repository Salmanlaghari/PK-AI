package com.salmanlaghari.pkai.data.remote.provider

import com.salmanlaghari.pkai.data.model.LlmProvider
import com.salmanlaghari.pkai.data.remote.PublicFreeApiService
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class AiProviderFactoryTest {

    private lateinit var mockOkHttpClient: OkHttpClient
    private lateinit var mockPublicFreeApiService: PublicFreeApiService
    private lateinit var factory: AiProviderFactory

    @Before
    fun setUp() {
        mockOkHttpClient = mock(OkHttpClient::class.java)
        mockPublicFreeApiService = mock(PublicFreeApiService::class.java)
        factory = AiProviderFactory(mockOkHttpClient, mockPublicFreeApiService)
    }

    @Test
    fun `getPublicFreeProvider returns PublicFreeAiProvider`() = runTest {
        assertTrue(factory.getPublicFreeProvider() is PublicFreeAiProvider)
    }

    @Test
    fun `getDefaultProvider returns the default provider (Groq) as openai-compatible`() = runTest {
        assertTrue(factory.getDefaultProvider() is OpenAiCompatibleProvider)
    }

    @Test
    fun `openai-compatible providers share OpenAiCompatibleProvider`() = runTest {
        assertTrue(factory.getProvider("groq") is OpenAiCompatibleProvider)
        assertTrue(factory.getProvider("llm7") is OpenAiCompatibleProvider)
        assertTrue(factory.getProvider("mistral") is OpenAiCompatibleProvider)
        assertTrue(factory.getProvider("cerebras") is OpenAiCompatibleProvider)
        assertTrue(factory.getProvider("huggingface") is OpenAiCompatibleProvider)
    }

    @Test
    fun `cloudflare uses its own adapter`() = runTest {
        assertTrue(factory.getProvider("cloudflare") is CloudflareWorkersAiProvider)
    }

    @Test
    fun `cohere uses its own adapter`() = runTest {
        assertTrue(factory.getProvider("cohere") is CohereAiProvider)
    }

    @Test
    fun `unknown provider id falls back to default`() = runTest {
        assertTrue(factory.getProvider("does-not-exist") is OpenAiCompatibleProvider)
    }
}
