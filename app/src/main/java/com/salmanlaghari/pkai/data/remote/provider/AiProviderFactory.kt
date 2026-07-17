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
    private val cohereApiService: com.salmanlaghari.pkai.data.remote.CohereApiService
) {
    fun getProvider(model: AiModel): AiProvider {
        return when (model) {
            AiModel.GEMINI -> GeminiAiProvider(geminiApiService)
            AiModel.CHATGPT -> {
                // OpenAI is disabled in selection, but fallback to OpenRouter or Cohere if accessed
                val cohereKey = com.salmanlaghari.pkai.BuildConfig.COHERE_API_KEY
                if (cohereKey.isNotBlank()) {
                    CohereAiProvider(cohereApiService)
                } else {
                    OpenRouterAiProvider(model, openRouterApiService)
                }
            }
            AiModel.CLAUDE,
            AiModel.GROK,
            AiModel.DEEPSEEK,
            AiModel.QWEN,
            AiModel.LLAMA,
            AiModel.MISTRAL,
            AiModel.PERPLEXITY -> OpenRouterAiProvider(model, openRouterApiService)
        }
    }
}
