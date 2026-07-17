package com.salmanlaghari.pkai.data.remote.provider

import com.salmanlaghari.pkai.data.model.AiModel
import com.salmanlaghari.pkai.data.remote.ApiService
import com.salmanlaghari.pkai.data.remote.GeminiApiService
import com.salmanlaghari.pkai.data.remote.OpenRouterApiService
import com.salmanlaghari.pkai.data.remote.GroqApiService
import com.salmanlaghari.pkai.data.remote.TogetherApiService
import com.salmanlaghari.pkai.data.remote.OpenAiApiService
import com.salmanlaghari.pkai.data.remote.CerebrasApiService
import com.salmanlaghari.pkai.data.remote.SambaNovaApiService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class AiProviderFactoryTest {

    private lateinit var mockApiService: ApiService
    private lateinit var mockGeminiApiService: GeminiApiService
    private lateinit var mockOpenRouterApiService: OpenRouterApiService
    private lateinit var mockGroqApiService: GroqApiService
    private lateinit var mockTogetherApiService: TogetherApiService
    private lateinit var mockOpenAiApiService: OpenAiApiService
    private lateinit var mockCerebrasApiService: CerebrasApiService
    private lateinit var mockSambaNovaApiService: SambaNovaApiService
    private lateinit var mockCohereApiService: com.salmanlaghari.pkai.data.remote.CohereApiService
    private lateinit var factory: AiProviderFactory

    @Before
    fun setUp() {
        mockApiService = mock(ApiService::class.java)
        mockGeminiApiService = mock(GeminiApiService::class.java)
        mockOpenRouterApiService = mock(OpenRouterApiService::class.java)
        mockGroqApiService = mock(GroqApiService::class.java)
        mockTogetherApiService = mock(TogetherApiService::class.java)
        mockOpenAiApiService = mock(OpenAiApiService::class.java)
        mockCerebrasApiService = mock(CerebrasApiService::class.java)
        mockSambaNovaApiService = mock(SambaNovaApiService::class.java)
        mockCohereApiService = mock(com.salmanlaghari.pkai.data.remote.CohereApiService::class.java)

        factory = AiProviderFactory(
            mockApiService,
            mockGeminiApiService,
            mockOpenRouterApiService,
            mockGroqApiService,
            mockTogetherApiService,
            mockOpenAiApiService,
            mockCerebrasApiService,
            mockSambaNovaApiService,
            mockCohereApiService
        )
    }

    @Test
    fun `getProvider returns correct real provider`() = runTest {
        val geminiProvider = factory.getProvider(AiModel.GEMINI)
        assertTrue(geminiProvider is GeminiAiProvider)

        val qwenProvider = factory.getProvider(AiModel.QWEN)
        assertTrue(qwenProvider is OpenRouterAiProvider)

        val grokProvider = factory.getProvider(AiModel.GROK)
        assertTrue(grokProvider is OpenRouterAiProvider)

        val mistralProvider = factory.getProvider(AiModel.MISTRAL)
        assertTrue(mistralProvider is OpenRouterAiProvider)

        val chatgptProvider = factory.getProvider(AiModel.CHATGPT)
        assertTrue(chatgptProvider is OpenRouterAiProvider || chatgptProvider is CohereAiProvider)

        val llamaProvider = factory.getProvider(AiModel.LLAMA)
        assertTrue(llamaProvider is OpenRouterAiProvider)

        val perplexityProvider = factory.getProvider(AiModel.PERPLEXITY)
        assertTrue(perplexityProvider is OpenRouterAiProvider)
    }
}
