package com.salmanlaghari.pkai.data.remote.provider

import com.salmanlaghari.pkai.data.model.AiModel
import com.salmanlaghari.pkai.data.remote.ApiService
import com.salmanlaghari.pkai.data.remote.ChatCompletionRequest
import com.salmanlaghari.pkai.data.remote.ChatMessageDto

interface AiProvider {
    suspend fun generateResponse(prompt: String): String
}

class PlaceholderAiProvider(private val model: AiModel) : AiProvider {
    override suspend fun generateResponse(prompt: String): String {
        // Simulate premium AI thoughts/processing
        kotlinx.coroutines.delay(1500)
        return when (model) {
            AiModel.GEMINI -> "Greetings from Gemini! I am Google's highly advanced multimodal intelligence model. How can I assist you in building premium concepts today?"
            AiModel.CHATGPT -> "Hello! I am ChatGPT by OpenAI, powered by the state-of-the-art GPT architecture. Ready to co-write, brainstorm, or explore ideas with you."
            AiModel.CLAUDE -> "Welcome! I am Claude, an advanced model created by Anthropic. I specialize in safe, deeply structured, and exceptionally detailed text reasoning."
            AiModel.GROK -> "Grok here! Ready to slice through facts with real-time understanding and a touch of wit. What's on your mind?"
            AiModel.DEEPSEEK -> "Greetings from DeepSeek! I am highly optimized for mathematical reasoning, science, coding, and complex problem solving."
            AiModel.QWEN -> "Hello! I am Qwen, Alibaba's top-tier language model. I am excellent at multilingual synthesis and logical calculations."
            AiModel.LLAMA -> "Hi there! I am Llama, Meta's open-weights model. I provide high-performance text comprehension and logical output."
            AiModel.MISTRAL -> "Welcome! I am Mistral, a highly optimized, high-efficiency model crafted in France. Let's solve things quickly and elegantly!"
            AiModel.PERPLEXITY -> "Hello! I am Perplexity. I specialize in contextual search, research summarization, and citation-based logical thinking."
        }
    }
}

class NetworkAiProvider(
    private val model: AiModel,
    private val apiService: ApiService
) : AiProvider {
    override suspend fun generateResponse(prompt: String): String {
        val request = ChatCompletionRequest(
            model = model.name.lowercase(),
            messages = listOf(ChatMessageDto(role = "user", content = prompt))
        )
        return try {
            val response = apiService.generateChatResponse(request)
            response.choices.firstOrNull()?.message?.content ?: "Empty response from server"
        } catch (e: Exception) {
            "Error: ${e.localizedMessage ?: "Unknown network error"}"
        }
    }
}
