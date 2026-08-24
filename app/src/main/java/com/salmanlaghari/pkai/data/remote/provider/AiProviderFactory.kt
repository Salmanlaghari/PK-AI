package com.salmanlaghari.pkai.data.remote.provider

import com.salmanlaghari.pkai.BuildConfig
import com.salmanlaghari.pkai.data.model.FreeAiModel
import com.salmanlaghari.pkai.data.model.LlmProvider
import com.salmanlaghari.pkai.data.model.ProviderFormat
import com.salmanlaghari.pkai.data.remote.PublicFreeApiService
import com.google.gson.Gson
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the correct [AiProvider] implementation for the user's selected provider.
 *
 * OpenAI-compatible providers (Groq, LLM7.io, Mistral) share [OpenAiCompatibleProvider];
 * Cohere gets its own adapter.
 *
 * API keys are read from BuildConfig (injected at build time from local.properties /
 * CI secrets) — never hardcoded in source.
 */
/**
 * The free-tier fallback order used when the user's active provider is rate-limited
 * (HTTP 429) or hits its quota (HTTP 402). When a request fails for one of those
 * reasons, PK AI retries against the next provider that has an API key configured.
 *
 * This is a single editable constant so the order can be tweaked in one place.
 */
val FALLBACK_ORDER: List<String> = listOf(
    "groq", "llm7", "mistral", "cohere"
)

@Singleton
class AiProviderFactory @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val publicFreeApiService: PublicFreeApiService
) {
    private val gson = Gson()

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    private val openAiServiceCache = mutableMapOf<String, OpenAiCompatibleApiService>()

    private fun openAiService(provider: LlmProvider): OpenAiCompatibleApiService =
        openAiServiceCache.getOrPut(provider.id) {
            retrofit(provider.baseUrl).create(OpenAiCompatibleApiService::class.java)
        }

    private val cohereService: CohereApiService by lazy {
        retrofit(LlmProvider.fromId("cohere").baseUrl)
            .create(CohereApiService::class.java)
    }

    /** Returns the provider implementation for the given provider id. */
    fun getProvider(providerId: String): AiProvider {
        val provider = LlmProvider.fromId(providerId)
        return when (provider.format) {
            ProviderFormat.OPENAI -> OpenAiCompatibleProvider(
                provider,
                keyFor(provider),
                openAiService(provider)
            )
            ProviderFormat.COHERE -> CohereAiProvider(
                provider,
                keyFor(provider),
                cohereService
            )
        }
    }

    /** Returns the key-less public provider used by the Free AI tab. */
    fun getPublicFreeProvider(): AiProvider = PublicFreeAiProvider(publicFreeApiService)

    private val pollinationsService: PollinationsApiService by lazy {
        retrofit("https://text.pollinations.ai/").create(PollinationsApiService::class.java)
    }

    /**
     * Returns the key-less provider backing the given [FreeAiModel] id.
     *
     * Used by the Home screen's "Free AI" tab, where the user can switch between the free
     * models without ever supplying an API key.
     */
    fun getFreeProvider(freeModelId: String): AiProvider =
        when (FreeAiModel.fromId(freeModelId).id) {
            FreeAiModel.FREE_LLM.id -> KeylessLlmAiProvider(
                pollinations = pollinationsService,
                // LLM7 accepts anonymous requests, so this reuses the OpenAI-compatible
                // adapter without ever reading an API key.
                llm7 = openAiService(LlmProvider.fromId("llm7"))
            )
            FreeAiModel.OX_ALPHA.id -> OxAlphaProvider(okHttpClient)
            else -> PublicFreeAiProvider(publicFreeApiService)
        }

    /** Returns the user's default provider (Groq). */
    fun getDefaultProvider(): AiProvider = getProvider(LlmProvider.DEFAULT.id)

    /**
     * True when the provider can actually be called right now — i.e. its API key (and, for
     * Cloudflare, the account id) is configured. Used to prune the fallback chain so we never
     * waste a request on a provider the user hasn't set up.
     */
    fun hasConfiguredKey(provider: LlmProvider): Boolean = keyFor(provider).isNotBlank()

    /**
     * Builds the ordered list of providers to try for a given starting provider.
     *
     * The selected provider is always first; the remaining slots follow [FALLBACK_ORDER]
     * (with the selected provider de-duplicated). Providers without a configured key are
     * dropped, so the chain only ever contains providers PK AI can actually call.
     */
    fun fallbackChain(selectedId: String): List<LlmProvider> {
        val orderedIds = listOf(selectedId) + FALLBACK_ORDER.filter { it != selectedId }
        return orderedIds
            .mapNotNull { id -> LlmProvider.ALL.firstOrNull { it.id == id } }
            .distinctBy { it.id }
            .filter { hasConfiguredKey(it) }
    }

    private fun keyFor(provider: LlmProvider): String = when (provider.apiKeyBuildConfig) {
        "GROQ_API_KEY" -> BuildConfig.GROQ_API_KEY
        "LLM7_API_KEY" -> BuildConfig.LLM7_API_KEY
        "MISTRAL_API_KEY" -> BuildConfig.MISTRAL_API_KEY
        "COHERE_API_KEY" -> BuildConfig.COHERE_API_KEY
        else -> ""
    }
}
