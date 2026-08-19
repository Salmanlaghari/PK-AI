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
 * OpenAI-compatible providers (Groq, LLM7.io, Mistral, Cerebras, Hugging Face) all
 * share [OpenAiCompatibleProvider]; Cloudflare and Cohere get their own adapters.
 *
 * API keys are read from BuildConfig (injected at build time from local.properties /
 * CI secrets) — never hardcoded in source.
 */
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

    private val cloudflareService: CloudflareWorkersApiService by lazy {
        retrofit(LlmProvider.fromId("cloudflare").baseUrl)
            .create(CloudflareWorkersApiService::class.java)
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
            ProviderFormat.CLOUDFLARE -> CloudflareWorkersAiProvider(
                provider,
                BuildConfig.CLOUDFLARE_API_TOKEN,
                BuildConfig.CLOUDFLARE_ACCOUNT_ID,
                cloudflareService
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
            else -> PublicFreeAiProvider(publicFreeApiService)
        }

    /** Returns the user's default provider (Groq). */
    fun getDefaultProvider(): AiProvider = getProvider(LlmProvider.DEFAULT.id)

    private fun keyFor(provider: LlmProvider): String = when (provider.apiKeyBuildConfig) {
        "GROQ_API_KEY" -> BuildConfig.GROQ_API_KEY
        "LLM7_API_KEY" -> BuildConfig.LLM7_API_KEY
        "MISTRAL_API_KEY" -> BuildConfig.MISTRAL_API_KEY
        "CEREBRAS_API_KEY" -> BuildConfig.CEREBRAS_API_KEY
        "HUGGINGFACE_API_KEY" -> BuildConfig.HUGGINGFACE_API_KEY
        "COHERE_API_KEY" -> BuildConfig.COHERE_API_KEY
        else -> ""
    }
}
