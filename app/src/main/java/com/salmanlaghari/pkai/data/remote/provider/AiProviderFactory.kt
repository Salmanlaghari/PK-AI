package com.salmanlaghari.pkai.data.remote.provider

import com.salmanlaghari.pkai.data.model.AiModel
import com.salmanlaghari.pkai.data.remote.ApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiProviderFactory @Inject constructor(
    private val apiService: ApiService,
    private val geminiApiService: com.salmanlaghari.pkai.data.remote.GeminiApiService,
    private val openRouterApiService: com.salmanlaghari.pkai.data.remote.OpenRouterApiService,
    private val groqApiService: com.salmanlaghari.pkai.data.remote.GroqApiService,
    private val togetherApiService: com.salmanlaghari.pkai.data.remote.TogetherApiService,
    private val openAiApiService: com.salmanlaghari.pkai.data.remote.OpenAiApiService,
    private val cerebrasApiService: com.salmanlaghari.pkai.data.remote.CerebrasApiService,
    private val sambaNovaApiService: com.salmanlaghari.pkai.data.remote.SambaNovaApiService,
    private val cohereApiService: com.salmanlaghari.pkai.data.remote.CohereApiService,
    private val anthropicApiService: com.salmanlaghari.pkai.data.remote.AnthropicApiService,
    private val xAiApiService: com.salmanlaghari.pkai.data.remote.XAiApiService,
    private val publicFreeApiService: com.salmanlaghari.pkai.data.remote.PublicFreeApiService
) {
    fun getPublicFreeProvider(): AiProvider {
        return PublicFreeAiProvider(publicFreeApiService)
    }

    /**
     * Unified "PK AI" premium provider. Always routes through OpenRouter using the
     * verified-working `openai/gpt-4o-mini` model so the chat returns a real response
     * without exposing any provider/model name to the user. (Restores the #28 OpenRouter
     * behaviour that the removed model selector previously provided.)
     */
    fun getPkAiProvider(): AiProvider {
        return OpenRouterAiProvider(AiModel.CHATGPT, openRouterApiService)
    }

    fun getProvider(model: AiModel): AiProvider {
        // Return Coming Soon placeholder for unavailable providers
        if (model.comingSoon) {
            return object : AiProvider {
                override suspend fun generateResponse(prompt: String): String {
                    return "⏳ **${model.displayName}** is coming soon!\n\nWe're working hard to integrate ${model.providerName}'s AI capabilities. Stay tuned for updates!"
                }
            }
        }
        return when (model) {
            AiModel.GEMINI -> GeminiAiProvider(geminiApiService)
            AiModel.CHATGPT -> {
                // Since user didn't supply an explicit OpenAI key, fallback to Cohere (or OpenRouter)
                val openaiKey = com.salmanlaghari.pkai.BuildConfig.OPENAI_API_KEY
                val cohereKey = com.salmanlaghari.pkai.BuildConfig.COHERE_API_KEY
                if (openaiKey.isNotBlank()) {
                    OpenAiAiProvider(model, openAiApiService)
                } else if (cohereKey.isNotBlank()) {
                    CohereAiProvider(cohereApiService)
                } else {
                    // Default to OpenRouter since it can also serve ChatGPT model IDs
                    OpenRouterAiProvider(model, openRouterApiService)
                }
            }
            AiModel.CLAUDE -> {
                // Prefer the native Anthropic API; fall back to OpenRouter if no key is configured.
                if (com.salmanlaghari.pkai.BuildConfig.ANTHROPIC_API_KEY.isNotBlank()) {
                    AnthropicAiProvider(anthropicApiService)
                } else {
                    OpenRouterAiProvider(model, openRouterApiService)
                }
            }
            AiModel.GROK -> {
                // Prefer the native xAI (Grok) API; fall back to Groq if no key is configured.
                if (com.salmanlaghari.pkai.BuildConfig.XAI_API_KEY.isNotBlank()) {
                    XAiGrokAiProvider(xAiApiService)
                } else {
                    GroqAiProvider(model, groqApiService)
                }
            }
            AiModel.DEEPSEEK -> OpenRouterAiProvider(model, openRouterApiService)
            AiModel.QWEN -> OpenRouterAiProvider(model, openRouterApiService)
            AiModel.LLAMA -> CerebrasAiProvider(model, cerebrasApiService)
            AiModel.MISTRAL -> TogetherAiProvider(model, togetherApiService)
            AiModel.PERPLEXITY -> SambaNovaAiProvider(model, sambaNovaApiService)
            AiModel.WEB -> OpenRouterAiProvider(model, openRouterApiService)
        }
    }
}
