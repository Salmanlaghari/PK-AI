package com.salmanlaghari.pkai.data.remote.provider

import com.salmanlaghari.pkai.BuildConfig
import com.salmanlaghari.pkai.data.local.secure.GeminiKeyStore
import com.salmanlaghari.pkai.data.model.FreeAiModel
import com.salmanlaghari.pkai.data.model.LlmProvider
import com.salmanlaghari.pkai.data.remote.PublicFreeApiService
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class AiProviderFactoryTest {

    private lateinit var mockOkHttpClient: OkHttpClient
    private lateinit var mockPublicFreeApiService: PublicFreeApiService
    private lateinit var mockGeminiKeyStore: GeminiKeyStore
    private lateinit var factory: AiProviderFactory

    @Before
    fun setUp() {
        mockOkHttpClient = mock(OkHttpClient::class.java)
        mockPublicFreeApiService = mock(PublicFreeApiService::class.java)
        mockGeminiKeyStore = mock(GeminiKeyStore::class.java)
        factory = AiProviderFactory(mockOkHttpClient, mockPublicFreeApiService, mockGeminiKeyStore)
    }

    @Test
    fun `gemini prefers user BYOK key over shared build key`() = runTest {
        val userKey = "user-supplied-key"
        org.mockito.Mockito.`when`(mockGeminiKeyStore.getApiKey()).thenReturn(userKey)
        // The resolved key must be EXACTLY the user's BYOK key — not merely
        // non-blank — proving the user's own quota is consumed even when the
        // shared BuildConfig key is also set.
        assertEquals(userKey, factory.keyFor(LlmProvider.fromId("gemini")))
        assertTrue(factory.hasConfiguredKey(LlmProvider.fromId("gemini")))
    }

    @Test
    fun `gemini falls back to shared build key when no user key stored`() = runTest {
        org.mockito.Mockito.`when`(mockGeminiKeyStore.getApiKey()).thenReturn(null)
        assertEquals(BuildConfig.GEMINI_API_KEY, factory.keyFor(LlmProvider.fromId("gemini")))
    }

    @Test
    fun `getPublicFreeProvider returns PublicFreeAiProvider`() = runTest {
        assertTrue(factory.getPublicFreeProvider() is PublicFreeAiProvider)
    }

    @Test
    fun `getDefaultProvider returns configured provider or keyless fallback`() = runTest {
        val selected = factory.defaultProviderId()
        val result = factory.getDefaultProvider()
        if (selected == null) {
            assertTrue(result is KeylessLlmAiProvider)
        } else {
            assertTrue(result !is KeylessLlmAiProvider)
        }
    }

    @Test
    fun `openai-compatible providers share OpenAiCompatibleProvider`() = runTest {
        assertTrue(factory.getProvider("gemini") is OpenAiCompatibleProvider)
        assertTrue(factory.getProvider("groq") is OpenAiCompatibleProvider)
        assertTrue(factory.getProvider("llm7") is OpenAiCompatibleProvider)
        assertTrue(factory.getProvider("mistral") is OpenAiCompatibleProvider)
    }

    @Test
    fun `cohere uses its own adapter`() = runTest {
        assertTrue(factory.getProvider("cohere") is CohereAiProvider)
    }

    @Test
    fun `unknown provider id falls back to default`() = runTest {
        assertTrue(factory.getProvider("does-not-exist") is OpenAiCompatibleProvider)
    }

    @Test
    fun `free AI tab resolves all key-less models`() = runTest {
        assertTrue(factory.getFreeProvider(FreeAiModel.PUBLIC_CHATBOT.id) is PublicFreeAiProvider)
        assertTrue(factory.getFreeProvider(FreeAiModel.FREE_LLM.id) is KeylessLlmAiProvider)
        // Ox Alpha is retired from FreeAiModel.ALL but the factory keeps a safe
        // route for stored prefs that still reference it.
        assertTrue(factory.getFreeProvider(FreeAiModel.OX_ALPHA.id) is KeylessLlmAiProvider ||
            factory.getFreeProvider(FreeAiModel.OX_ALPHA.id) is OxAlphaProvider)
    }

    @Test
    fun `unknown free model id falls back to the default free model`() = runTest {
        // FreeAiModel.DEFAULT is PK AI Free LLM (Ox Alpha was retired), so an
        // unknown id must resolve to the key-less LLM provider.
        assertTrue(factory.getFreeProvider("does-not-exist") is KeylessLlmAiProvider)
        assertTrue(factory.getFreeProvider("does-not-exist") !is PublicFreeAiProvider)
    }

    @Test
    fun `every provider declares a non-blank model id`() = runTest {
        // Guards against regressions where a model id is left empty or a provider is added
        // without one — the root cause of the earlier HTTP 404 failures.
        LlmProvider.ALL.forEach { provider ->
            assertTrue(
                "${provider.displayName} must declare a default model",
                provider.defaultModel.isNotBlank()
            )
            assertTrue(
                "${provider.displayName} baseUrl must end with '/' for Retrofit",
                provider.baseUrl.endsWith("/")
            )
        }
    }
}
