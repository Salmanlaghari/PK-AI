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

        factory = AiProviderFactory(
            mockApiService,
            mockGeminiApiService,
            mockOpenRouterApiService,
            mockGroqApiService,
            mockTogetherApiService,
            mockOpenAiApiService,
            mockCerebrasApiService,
            mockSambaNovaApiService
        )
    }

    @Test
    fun `getProvider returns PlaceholderAiProvider when usePlaceholder is true`() = runTest {
        val provider = factory.getProvider(AiModel.GEMINI, usePlaceholder = true)
        assertTrue(provider is PlaceholderAiProvider)

        val response = provider.generateResponse("test prompt")
        assertTrue(response.contains("Gemini"))
    }

    @Test
    fun `getProvider returns correct real provider when usePlaceholder is false`() = runTest {
        val geminiProvider = factory.getProvider(AiModel.GEMINI, usePlaceholder = false)
        assertTrue(geminiProvider is GeminiAiProvider)

        val qwenProvider = factory.getProvider(AiModel.QWEN, usePlaceholder = false)
        assertTrue(qwenProvider is OpenRouterAiProvider)

        val grokProvider = factory.getProvider(AiModel.GROK, usePlaceholder = false)
        assertTrue(grokProvider is GroqAiProvider)

        val mistralProvider = factory.getProvider(AiModel.MISTRAL, usePlaceholder = false)
        assertTrue(mistralProvider is TogetherAiProvider)

        val chatgptProvider = factory.getProvider(AiModel.CHATGPT, usePlaceholder = false)
        assertTrue(chatgptProvider is OpenAiAiProvider)

        val llamaProvider = factory.getProvider(AiModel.LLAMA, usePlaceholder = false)
        assertTrue(llamaProvider is CerebrasAiProvider)

        val perplexityProvider = factory.getProvider(AiModel.PERPLEXITY, usePlaceholder = false)
        assertTrue(perplexityProvider is SambaNovaAiProvider)
    }
}
