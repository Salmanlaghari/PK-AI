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
                // OpenRouter serves ChatGPT (gpt-4o-mini) reliably with the configured key.
                OpenRouterAiProvider(model, openRouterApiService)
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
                // Prefer the native xAI (Grok) API; fall back to OpenRouter if no key is configured.
                if (com.salmanlaghari.pkai.BuildConfig.XAI_API_KEY.isNotBlank()) {
                    XAiGrokAiProvider(xAiApiService)
                } else {
                    OpenRouterAiProvider(model, openRouterApiService)
                }
            }
            AiModel.DEEPSEEK -> OpenRouterAiProvider(model, openRouterApiService)
            AiModel.QWEN -> OpenRouterAiProvider(model, openRouterApiService)
            AiModel.LLAMA -> CerebrasAiProvider(model, cerebrasApiService)
            AiModel.MISTRAL -> {
                // OpenRouter serves Mistral reliably with the configured key.
                OpenRouterAiProvider(model, openRouterApiService)
            }
            AiModel.PERPLEXITY -> SambaNovaAiProvider(model, sambaNovaApiService)
        }
    }
}
