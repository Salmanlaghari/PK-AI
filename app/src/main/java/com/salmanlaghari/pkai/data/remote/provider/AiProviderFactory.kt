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
    private val sambaNovaApiService: com.salmanlaghari.pkai.data.remote.SambaNovaApiService
) {
    fun getProvider(model: AiModel, usePlaceholder: Boolean = false): AiProvider {
        if (usePlaceholder) {
            return PlaceholderAiProvider(model)
        }
        return when (model) {
            AiModel.GEMINI -> GeminiAiProvider(geminiApiService)
            AiModel.CHATGPT -> OpenAiAiProvider(model, openAiApiService)
            AiModel.CLAUDE -> OpenRouterAiProvider(model, openRouterApiService)
            AiModel.GROK -> GroqAiProvider(model, groqApiService)
            AiModel.DEEPSEEK -> OpenRouterAiProvider(model, openRouterApiService)
            AiModel.QWEN -> OpenRouterAiProvider(model, openRouterApiService)
            AiModel.LLAMA -> CerebrasAiProvider(model, cerebrasApiService)
            AiModel.MISTRAL -> TogetherAiProvider(model, togetherApiService)
            AiModel.PERPLEXITY -> SambaNovaAiProvider(model, sambaNovaApiService)
        }
    }
}
