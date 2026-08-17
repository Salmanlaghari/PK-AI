package com.salmanlaghari.pkai.data.remote.provider

import com.salmanlaghari.pkai.data.model.AiModel
import com.salmanlaghari.pkai.data.remote.ApiService
import com.salmanlaghari.pkai.data.remote.OpenRouterApiService
import com.salmanlaghari.pkai.data.remote.TogetherApiService
import com.salmanlaghari.pkai.data.remote.CerebrasApiService
import com.salmanlaghari.pkai.data.remote.SambaNovaApiService
import com.salmanlaghari.pkai.data.remote.AnthropicApiService
import com.salmanlaghari.pkai.data.remote.PublicFreeApiService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class AiProviderFactoryTest {

    private lateinit var mockApiService: ApiService
    private lateinit var mockOpenRouterApiService: OpenRouterApiService
    private lateinit var mockTogetherApiService: TogetherApiService
    private lateinit var mockCerebrasApiService: CerebrasApiService
    private lateinit var mockSambaNovaApiService: SambaNovaApiService
    private lateinit var mockAnthropicApiService: AnthropicApiService
    private lateinit var mockPublicFreeApiService: PublicFreeApiService
    private lateinit var factory: AiProviderFactory

    @Before
    fun setUp() {
        mockApiService = mock(ApiService::class.java)
        mockOpenRouterApiService = mock(OpenRouterApiService::class.java)
        mockTogetherApiService = mock(TogetherApiService::class.java)
        mockCerebrasApiService = mock(CerebrasApiService::class.java)
        mockSambaNovaApiService = mock(SambaNovaApiService::class.java)
        mockAnthropicApiService = mock(AnthropicApiService::class.java)
        mockPublicFreeApiService = mock(PublicFreeApiService::class.java)

        factory = AiProviderFactory(
            mockApiService,
            mockOpenRouterApiService,
            mockTogetherApiService,
            mockCerebrasApiService,
            mockSambaNovaApiService,
            mockAnthropicApiService,
            mockPublicFreeApiService
        )
    }

    @Test
    fun `getPublicFreeProvider returns PublicFreeAiProvider`() = runTest {
        val provider = factory.getPublicFreeProvider()
        assertTrue(provider is PublicFreeAiProvider)
    }

    @Test
    fun `getPkAiProvider returns OpenRouterAiProvider`() = runTest {
        val provider = factory.getPkAiProvider()
        assertTrue(provider is OpenRouterAiProvider)
    }

    @Test
    fun `getProvider returns correct real provider`() = runTest {
        val qwenProvider = factory.getProvider(AiModel.QWEN)
        assertTrue(qwenProvider is OpenRouterAiProvider)

        val mistralProvider = factory.getProvider(AiModel.MISTRAL)
        assertTrue(mistralProvider is TogetherAiProvider)

        val deepseekProvider = factory.getProvider(AiModel.DEEPSEEK)
        assertTrue(deepseekProvider is OpenRouterAiProvider)

        val claudeProvider = factory.getProvider(AiModel.CLAUDE)
        assertTrue(claudeProvider is AnthropicAiProvider || claudeProvider is OpenRouterAiProvider)

        val webProvider = factory.getProvider(AiModel.WEB)
        assertTrue(webProvider is OpenRouterAiProvider)

        val llamaProvider = factory.getProvider(AiModel.LLAMA)
        if (AiModel.LLAMA.comingSoon) {
            val response = llamaProvider.generateResponse("test")
            assertTrue(response.contains("coming soon"))
        } else {
            assertTrue(llamaProvider is CerebrasAiProvider)
        }

        val perplexityProvider = factory.getProvider(AiModel.PERPLEXITY)
        if (AiModel.PERPLEXITY.comingSoon) {
            val response = perplexityProvider.generateResponse("test")
            assertTrue(response.contains("coming soon"))
        } else {
            assertTrue(perplexityProvider is SambaNovaAiProvider)
        }
    }
}
