package com.salmanlaghari.pkai.data.remote.provider

import com.salmanlaghari.pkai.data.model.AiModel
import com.salmanlaghari.pkai.data.remote.ApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiProviderFactory @Inject constructor(
    private val apiService: ApiService,
    private val openRouterApiService: com.salmanlaghari.pkai.data.remote.OpenRouterApiService,
    private val togetherApiService: com.salmanlaghari.pkai.data.remote.TogetherApiService,
    private val cerebrasApiService: com.salmanlaghari.pkai.data.remote.CerebrasApiService,
    private val sambaNovaApiService: com.salmanlaghari.pkai.data.remote.SambaNovaApiService,
    private val anthropicApiService: com.salmanlaghari.pkai.data.remote.AnthropicApiService,
    private val publicFreeApiService: com.salmanlaghari.pkai.data.remote.PublicFreeApiService
) {
    fun getPublicFreeProvider(): AiProvider {
        return PublicFreeAiProvider(publicFreeApiService)
    }

    /**
     * Unified "PK AI" premium provider. Always routes through OpenRouter using the
     * verified-working `deepseek/deepseek-chat` model so the chat returns a real response
     * without exposing any provider/model name to the user. (Preserves the #28/#29
     * OpenRouter behaviour; Gemini/Grok/ChatGPT/OpenAI were removed.)
     */
    fun getPkAiProvider(): AiProvider {
        return OpenRouterAiProvider(AiModel.DEEPSEEK, openRouterApiService)
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
            AiModel.CLAUDE -> {
                // Prefer the native Anthropic API; fall back to OpenRouter if no key is configured.
                if (com.salmanlaghari.pkai.BuildConfig.ANTHROPIC_API_KEY.isNotBlank()) {
                    AnthropicAiProvider(anthropicApiService)
                } else {
                    OpenRouterAiProvider(model, openRouterApiService)
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
