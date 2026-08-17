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

class PublicFreeAiProvider(
    private val apiService: com.salmanlaghari.pkai.data.remote.PublicFreeApiService
) : AiProvider {
    override suspend fun generateResponse(prompt: String): String {
        kotlinx.coroutines.delay(1000) // Beautiful premium conversational delay
        val lowerPrompt = prompt.lowercase()
        return try {
            if (lowerPrompt.contains("fact") || lowerPrompt.contains("know") || lowerPrompt.contains("something")) {
                val response = apiService.getFreeFact()
                "Here is an interesting public fact for you:\n\n${response["fact"] ?: "AI is the future!"}"
            } else if (lowerPrompt.contains("advice") || lowerPrompt.contains("help") || lowerPrompt.contains("suggest")) {
                val response = apiService.getFreeAdvice()
                val slip = response["slip"] as? Map<*, *>
                val advice = slip?.get("advice") as? String
                advice ?: "Stay positive and keep coding!"
            } else {
                val words = prompt.trim().split("\\s+".toRegex()).size
                "I am PK AI's **Free Public Chatbot** (No API Key required)!\n\nI processed your query (\"$prompt\") containing $words words using unauthenticated public endpoints. To experience ultra-fast reasoning with Gemini, Claude, or Grok, please configure your keys in the **Settings** and use the **Premium Chat** tab!"
            }
        } catch (e: Exception) {
            "I am PK AI's **Free Public Chatbot**!\n\nYour query: \"$prompt\"\n\nI am currently operating in smart offline mode. Switch to the **Premium Chat** tab at any time to utilize Gemini, Claude, or Grok!"
        }
    }
}

class CohereAiProvider(
    private val apiService: com.salmanlaghari.pkai.data.remote.CohereApiService
) : AiProvider {
    override suspend fun generateResponse(prompt: String): String {
        val apiKey = com.salmanlaghari.pkai.BuildConfig.COHERE_API_KEY
        if (apiKey.isBlank()) {
            return "API key not configured."
        }
        val request = com.salmanlaghari.pkai.data.remote.CohereChatRequest(
            message = prompt,
            model = "command-r-plus"
        )
        return try {
            val response = apiService.generateChatResponse("Bearer $apiKey", request)
            response.text ?: "Empty response from Cohere server."
        } catch (e: Exception) {
            "Error: ${e.localizedMessage ?: "Unknown network error"}"
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

// --- Real Providers (Phase 4.3) ---

class GeminiAiProvider(
    private val apiService: com.salmanlaghari.pkai.data.remote.GeminiApiService
) : AiProvider {
    override suspend fun generateResponse(prompt: String): String {
        val apiKey = com.salmanlaghari.pkai.BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            return "API key not configured."
        }
        val request = com.salmanlaghari.pkai.data.remote.GeminiRequest(
            contents = listOf(
                com.salmanlaghari.pkai.data.remote.GeminiContent(
                    parts = listOf(com.salmanlaghari.pkai.data.remote.GeminiPart(prompt))
                )
            )
        )
        return try {
            val response = apiService.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Empty response from Gemini server."
        } catch (e: Exception) {
            "Error: ${e.localizedMessage ?: "Unknown network error"}"
        }
    }
}

class OpenRouterAiProvider(
    private val model: AiModel,
    private val apiService: com.salmanlaghari.pkai.data.remote.OpenRouterApiService
) : AiProvider {
    override suspend fun generateResponse(prompt: String): String {
        val apiKey = com.salmanlaghari.pkai.BuildConfig.OPENROUTER_API_KEY
        if (apiKey.isBlank()) {
            return "OpenRouter API Key not configured. Please supply an API key."
        }
        val modelId = when (model) {
            AiModel.QWEN -> "qwen/qwen-2.5-72b-instruct"
            AiModel.DEEPSEEK -> "deepseek/deepseek-chat"
            AiModel.LLAMA -> "meta-llama/llama-3.1-8b-instruct"
            AiModel.MISTRAL -> "mistralai/mistral-7b-instruct"
            AiModel.CHATGPT -> "openai/gpt-4o-mini"
            AiModel.CLAUDE -> "anthropic/claude-3-haiku"
            AiModel.PERPLEXITY -> "perplexity/sonar-chat"
            else -> "google/gemma-2-9b-it"
        }
        val request = ChatCompletionRequest(
            model = modelId,
            messages = listOf(ChatMessageDto(role = "user", content = prompt))
        )
        return try {
            val response = apiService.generateChatResponse(
                authorization = "Bearer $apiKey",
                referer = "https://pkai.salmanlaghari.com",
                title = "PK AI",
                request = request
            )
            val result = response.choices.firstOrNull()?.message?.content
            if (result.isNullOrBlank()) {
                "Error: Received empty response from OpenRouter ($modelId). Please retry."
            } else {
                result
            }
        } catch (e: retrofit2.HttpException) {
            val code = e.code()
            if (code == 401 || code == 403) {
                "Error: OpenRouter Authentication failed (HTTP $code). Verify your API Key."
            } else if (code == 429) {
                "Error: OpenRouter rate limit exceeded (HTTP 429). Please wait and try again."
            } else {
                "Error: OpenRouter error (HTTP $code). Server responded with: ${e.message()}"
            }
        } catch (e: java.io.IOException) {
            "Error: Timeout/Network connection failed. Check your internet connection."
        } catch (e: Exception) {
            "Error: ${e.localizedMessage ?: "Unknown OpenRouter connection issue."}"
        }
    }
}

class GroqAiProvider(
    private val model: AiModel,
    private val apiService: com.salmanlaghari.pkai.data.remote.GroqApiService
) : AiProvider {
    override suspend fun generateResponse(prompt: String): String {
        val apiKey = com.salmanlaghari.pkai.BuildConfig.GROQ_API_KEY
        if (apiKey.isBlank()) {
            return "API key not configured."
        }
        val modelId = when (model) {
            AiModel.GROK -> "llama3-8b-8192"
            AiModel.LLAMA -> "llama3-8b-8192"
            else -> "gemma2-9b-it"
        }
        val request = ChatCompletionRequest(
            model = modelId,
            messages = listOf(ChatMessageDto(role = "user", content = prompt))
        )
        return try {
            val response = apiService.generateChatResponse("Bearer $apiKey", request)
            response.choices.firstOrNull()?.message?.content ?: "Empty response from Groq server."
        } catch (e: Exception) {
            "Error: ${e.localizedMessage ?: "Unknown network error"}"
        }
    }
}

class TogetherAiProvider(
    private val model: AiModel,
    private val apiService: com.salmanlaghari.pkai.data.remote.TogetherApiService
) : AiProvider {
    override suspend fun generateResponse(prompt: String): String {
        val apiKey = com.salmanlaghari.pkai.BuildConfig.TOGETHER_API_KEY
        if (apiKey.isBlank()) {
            return "API key not configured."
        }
        val modelId = when (model) {
            AiModel.MISTRAL -> "mistralai/Mistral-7B-Instruct-v0.1"
            AiModel.LLAMA -> "meta-llama/Meta-Llama-3.1-8B-Instruct-Turbo"
            else -> "meta-llama/Meta-Llama-3-8B-Instruct-Lite"
        }
        val request = ChatCompletionRequest(
            model = modelId,
            messages = listOf(ChatMessageDto(role = "user", content = prompt))
        )
        return try {
            val response = apiService.generateChatResponse("Bearer $apiKey", request)
            response.choices.firstOrNull()?.message?.content ?: "Empty response from Together AI server."
        } catch (e: Exception) {
            "Error: ${e.localizedMessage ?: "Unknown network error"}"
        }
    }
}

class OpenAiAiProvider(
    private val model: AiModel,
    private val apiService: com.salmanlaghari.pkai.data.remote.OpenAiApiService
) : AiProvider {
    override suspend fun generateResponse(prompt: String): String {
        val apiKey = com.salmanlaghari.pkai.BuildConfig.OPENAI_API_KEY
        if (apiKey.isBlank()) {
            return "API key not configured."
        }
        val modelId = when (model) {
            AiModel.CHATGPT -> "gpt-4o-mini"
            else -> "gpt-3.5-turbo"
        }
        val request = ChatCompletionRequest(
            model = modelId,
            messages = listOf(ChatMessageDto(role = "user", content = prompt))
        )
        return try {
            val response = apiService.generateChatResponse("Bearer $apiKey", request)
            response.choices.firstOrNull()?.message?.content ?: "Empty response from OpenAI server."
        } catch (e: Exception) {
            "Error: ${e.localizedMessage ?: "Unknown network error"}"
        }
    }
}

class CerebrasAiProvider(
    private val model: AiModel,
    private val apiService: com.salmanlaghari.pkai.data.remote.CerebrasApiService
) : AiProvider {
    override suspend fun generateResponse(prompt: String): String {
        val apiKey = com.salmanlaghari.pkai.BuildConfig.CEREBRAS_API_KEY
        if (apiKey.isBlank()) {
            return "API key not configured."
        }
        val modelId = "llama3.1-8b"
        val request = ChatCompletionRequest(
            model = modelId,
            messages = listOf(ChatMessageDto(role = "user", content = prompt))
        )
        return try {
            val response = apiService.generateChatResponse("Bearer $apiKey", request)
            response.choices.firstOrNull()?.message?.content ?: "Empty response from Cerebras server."
        } catch (e: Exception) {
            "Error: ${e.localizedMessage ?: "Unknown network error"}"
        }
    }
}

class SambaNovaAiProvider(
    private val model: AiModel,
    private val apiService: com.salmanlaghari.pkai.data.remote.SambaNovaApiService
) : AiProvider {
    override suspend fun generateResponse(prompt: String): String {
        val apiKey = com.salmanlaghari.pkai.BuildConfig.SAMBANOVA_API_KEY
        if (apiKey.isBlank()) {
            return "API key not configured."
        }
        val modelId = "Meta-Llama-3.1-8B-Instruct"
        val request = ChatCompletionRequest(
            model = modelId,
            messages = listOf(ChatMessageDto(role = "user", content = prompt))
        )
        return try {
            val response = apiService.generateChatResponse("Bearer $apiKey", request)
            response.choices.firstOrNull()?.message?.content ?: "Empty response from SambaNova server."
        } catch (e: Exception) {
            "Error: ${e.localizedMessage ?: "Unknown network error"}"
        }
    }
}

// --- Native Anthropic (Claude) Provider (Phase 4.4) ---

class AnthropicAiProvider(
    private val apiService: com.salmanlaghari.pkai.data.remote.AnthropicApiService
) : AiProvider {
    override suspend fun generateResponse(prompt: String): String {
        val apiKey = com.salmanlaghari.pkai.BuildConfig.ANTHROPIC_API_KEY
        if (apiKey.isBlank()) {
            return "Anthropic API Key not configured. Please supply an API key."
        }
        val request = com.salmanlaghari.pkai.data.remote.AnthropicRequest(
            messages = listOf(
                com.salmanlaghari.pkai.data.remote.AnthropicMessage(
                    role = "user",
                    content = prompt
                )
            )
        )
        return try {
            val response = apiService.createMessage(apiKey, request = request)
            val text = response.content
                ?.firstOrNull { it.type == "text" }
                ?.text
            if (text.isNullOrBlank()) {
                "Error: Received empty response from Anthropic. Please retry."
            } else {
                text
            }
        } catch (e: retrofit2.HttpException) {
            val code = e.code()
            when {
                code == 401 || code == 403 -> "Error: Anthropic Authentication failed (HTTP $code). Verify your API Key."
                code == 429 -> "Error: Anthropic rate limit exceeded (HTTP 429). Please wait and try again."
                else -> "Error: Anthropic error (HTTP $code). Server responded with: ${e.message()}"
            }
        } catch (e: java.io.IOException) {
            "Error: Timeout/Network connection failed. Check your internet connection."
        } catch (e: Exception) {
            "Error: ${e.localizedMessage ?: "Unknown Anthropic connection issue."}"
        }
    }
}

// --- Native xAI (Grok) Provider (Phase 4.4) ---

class XAiGrokAiProvider(
    private val apiService: com.salmanlaghari.pkai.data.remote.XAiApiService
) : AiProvider {
    override suspend fun generateResponse(prompt: String): String {
        val apiKey = com.salmanlaghari.pkai.BuildConfig.XAI_API_KEY
        if (apiKey.isBlank()) {
            return "xAI API Key not configured. Please supply an API key."
        }
        val modelId = "grok-2-latest"
        val request = ChatCompletionRequest(
            model = modelId,
            messages = listOf(ChatMessageDto(role = "user", content = prompt))
        )
        return try {
            val response = apiService.generateChatResponse("Bearer $apiKey", request)
            response.choices.firstOrNull()?.message?.content
                ?: "Error: Received empty response from xAI. Please retry."
        } catch (e: retrofit2.HttpException) {
            val code = e.code()
            when {
                code == 401 || code == 403 -> "Error: xAI Authentication failed (HTTP $code). Verify your API Key."
                code == 429 -> "Error: xAI rate limit exceeded (HTTP 429). Please wait and try again."
                else -> "Error: xAI error (HTTP $code). Server responded with: ${e.message()}"
            }
        } catch (e: java.io.IOException) {
            "Error: Timeout/Network connection failed. Check your internet connection."
        } catch (e: Exception) {
            "Error: ${e.localizedMessage ?: "Unknown xAI connection issue."}"
        }
    }
}
