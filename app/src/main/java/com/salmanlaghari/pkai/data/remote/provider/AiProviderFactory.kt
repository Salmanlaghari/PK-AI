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
                val openaiKey = com.salmanlaghari.pkai.BuildConfig.OPENAI_API_KEY
                if (openaiKey.isNotBlank()) {
                    OpenAiAiProvider(model, openAiApiService)
                } else {
                    object : AiProvider {
                        override fun generateResponseStream(prompt: String): kotlinx.coroutines.flow.Flow<String> {
                            return kotlinx.coroutines.flow.flow {
                                emit("ChatGPT/OpenAI is currently Coming Soon.")
                            }
                        }
                    }
                }
            }
            AiModel.CLAUDE -> OpenRouterAiProvider(model, openRouterApiService)
            AiModel.GROK -> OpenRouterAiProvider(model, openRouterApiService)
            AiModel.DEEPSEEK -> OpenRouterAiProvider(model, openRouterApiService)
            AiModel.QWEN -> OpenRouterAiProvider(model, openRouterApiService)
            AiModel.LLAMA -> OpenRouterAiProvider(model, openRouterApiService)
            AiModel.MISTRAL -> OpenRouterAiProvider(model, openRouterApiService)
            AiModel.PERPLEXITY -> OpenRouterAiProvider(model, openRouterApiService)
        }
    }
}
