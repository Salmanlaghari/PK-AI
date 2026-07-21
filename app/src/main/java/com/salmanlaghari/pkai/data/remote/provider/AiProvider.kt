package com.salmanlaghari.pkai.data.remote.provider

import com.salmanlaghari.pkai.data.model.AiModel
import com.salmanlaghari.pkai.data.remote.ApiService
import com.salmanlaghari.pkai.data.remote.ChatCompletionRequest
import com.salmanlaghari.pkai.data.remote.ChatMessageDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.ResponseBody
import java.io.BufferedReader

interface AiProvider {
    fun generateResponseStream(prompt: String): Flow<String>
}

// --- SSE Streaming helper ---
fun parseSseStream(
    responseBody: ResponseBody,
    extractDelta: (String) -> String?
): Flow<String> = flow {
    var accumulated = ""
    val reader = BufferedReader(responseBody.charStream())
    try {
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val trimmed = line!!.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("data:")) {
                val dataContent = trimmed.substring(5).trim()
                if (dataContent == "[DONE]") {
                    break
                }
                try {
                    val delta = extractDelta(dataContent)
                    if (delta != null) {
                        accumulated += delta
                        emit(accumulated)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SSE_PARSER", "Error parsing SSE chunk: $dataContent", e)
                }
            }
        }
    } finally {
        try {
            reader.close()
        } catch (e: Exception) {
            // Ignore
        }
    }
}

// OpenAI DTO structures for parsing streaming chunks
data class ChatDeltaDto(val content: String?)
data class ChatChunkChoiceDto(val delta: ChatDeltaDto?)
data class ChatCompletionChunk(val choices: List<ChatChunkChoiceDto>?)

// Gemini DTO structures for parsing streaming chunks
data class GeminiPartChunk(val text: String?)
data class GeminiCandidateContentChunk(val parts: List<GeminiPartChunk>?)
data class GeminiCandidateChunk(val content: GeminiCandidateContentChunk?)
data class GeminiResponseChunk(val candidates: List<GeminiCandidateChunk>?)

class PlaceholderAiProvider(private val model: AiModel) : AiProvider {
    override fun generateResponseStream(prompt: String): Flow<String> = flow {
        val fullText = when (model) {
            AiModel.GEMINI -> "Greetings from Gemini! I am Google's highly advanced streaming model. How can I assist you today?"
            AiModel.CHATGPT -> "Hello! I am ChatGPT by OpenAI, streaming from the state-of-the-art GPT architecture. How can I assist you?"
            AiModel.CLAUDE -> "Welcome! I am Claude, an advanced model created by Anthropic. I specialize in safe, deeply structured real-time reasoning."
            AiModel.GROK -> "Grok here! Ready to slice through facts with real-time understanding and a touch of wit. What's on your mind?"
            AiModel.DEEPSEEK -> "Greetings from DeepSeek! I am highly optimized for mathematical reasoning, science, and coding."
            AiModel.QWEN -> "Hello! I am Qwen, Alibaba's top-tier language model. Let's solve things elegantly!"
            AiModel.LLAMA -> "Hi there! I am Llama, Meta's open-weights model. How can I assist you?"
            AiModel.MISTRAL -> "Welcome! I am Mistral, a highly optimized, high-efficiency model crafted in France."
            AiModel.PERPLEXITY -> "Hello! I am Perplexity. I specialize in contextual search and citation-based logical thinking."
        }
        val words = fullText.split(" ")
        var currentText = ""
        for (i in words.indices) {
            currentText += if (i == 0) words[i] else " ${words[i]}"
            emit(currentText)
            kotlinx.coroutines.delay(100)
        }
    }
}

class CohereAiProvider(
    private val apiService: com.salmanlaghari.pkai.data.remote.CohereApiService
) : AiProvider {
    override fun generateResponseStream(prompt: String): Flow<String> = flow {
        val apiKey = com.salmanlaghari.pkai.BuildConfig.COHERE_API_KEY
        if (apiKey.isBlank()) {
            emit("API key not configured.")
            return@flow
        }
        val request = com.salmanlaghari.pkai.data.remote.CohereChatRequest(
            message = prompt,
            model = "command-r-plus"
        )
        try {
            val response = apiService.generateChatResponse("Bearer $apiKey", request)
            val fullText = response.text ?: "Empty response from Cohere server."
            val words = fullText.split(" ")
            var currentText = ""
            for (i in words.indices) {
                currentText += if (i == 0) words[i] else " ${words[i]}"
                emit(currentText)
                kotlinx.coroutines.delay(80)
            }
        } catch (e: Exception) {
            emit("Error: ${e.localizedMessage ?: "Unknown network error"}")
        }
    }
}

class NetworkAiProvider(
    private val model: AiModel,
    private val apiService: ApiService
) : AiProvider {
    override fun generateResponseStream(prompt: String): Flow<String> = flow {
        val request = ChatCompletionRequest(
            model = model.name.lowercase(),
            messages = listOf(ChatMessageDto(role = "user", content = prompt))
        )
        try {
            val response = apiService.generateChatResponse(request)
            val fullText = response.choices?.firstOrNull()?.message?.content ?: "Empty response from server"
            val words = fullText.split(" ")
            var currentText = ""
            for (i in words.indices) {
                currentText += if (i == 0) words[i] else " ${words[i]}"
                emit(currentText)
                kotlinx.coroutines.delay(80)
            }
        } catch (e: Exception) {
            emit("Error: ${e.localizedMessage ?: "Unknown network error"}")
        }
    }
}

// --- Real Streaming Providers (Phase 4.3) ---

class GeminiAiProvider(
    private val apiService: com.salmanlaghari.pkai.data.remote.GeminiApiService
) : AiProvider {
    override fun generateResponseStream(prompt: String): Flow<String> = flow {
        val apiKey = com.salmanlaghari.pkai.BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            emit("API key not configured.")
            return@flow
        }
        val request = com.salmanlaghari.pkai.data.remote.GeminiRequest(
            contents = listOf(
                com.salmanlaghari.pkai.data.remote.GeminiContent(
                    parts = listOf(com.salmanlaghari.pkai.data.remote.GeminiPart(prompt))
                )
            )
        )
        try {
            val responseBody = apiService.streamGenerateContent(apiKey, alt = "sse", request = request)
            val gson = com.google.gson.Gson()
            parseSseStream(responseBody) { dataContent ->
                val chunk = gson.fromJson(dataContent, GeminiResponseChunk::class.java)
                chunk.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            }.collect { accumulated ->
                emit(accumulated)
            }
        } catch (e: Exception) {
            emit("Error: ${e.localizedMessage ?: "Unknown network error"}")
        }
    }
}

class OpenRouterAiProvider(
    private val model: AiModel,
    private val apiService: com.salmanlaghari.pkai.data.remote.OpenRouterApiService
) : AiProvider {
    override fun generateResponseStream(prompt: String): Flow<String> = flow {
        val apiKey = com.salmanlaghari.pkai.BuildConfig.OPENROUTER_API_KEY
        if (apiKey.isBlank()) {
            emit("API key not configured.")
            return@flow
        }
        val modelId = when (model) {
            AiModel.QWEN -> "qwen/qwen-2.5-72b-instruct"
            AiModel.DEEPSEEK -> "deepseek/deepseek-chat"
            AiModel.LLAMA -> "meta-llama/llama-3.3-70b-instruct"
            AiModel.MISTRAL -> "mistralai/mistral-nemo"
            AiModel.CHATGPT -> "openai/gpt-4o-mini"
            AiModel.CLAUDE -> "anthropic/claude-3.5-sonnet"
            AiModel.PERPLEXITY -> "perplexity/sonar"
            AiModel.GROK -> "x-ai/grok-2"
            else -> "google/gemma-2-9b-it"
        }
        val request = ChatCompletionRequest(
            model = modelId,
            messages = listOf(ChatMessageDto(role = "user", content = prompt)),
            stream = true
        )
        try {
            val responseBody = apiService.streamChatResponse("Bearer $apiKey", request = request)
            val gson = com.google.gson.Gson()
            parseSseStream(responseBody) { dataContent ->
                val chunk = gson.fromJson(dataContent, ChatCompletionChunk::class.java)
                chunk.choices?.firstOrNull()?.delta?.content
            }.collect { accumulated ->
                emit(accumulated)
            }
        } catch (e: Exception) {
            emit("Error: ${e.localizedMessage ?: "Unknown network error"}")
        }
    }
}

class GroqAiProvider(
    private val model: AiModel,
    private val apiService: com.salmanlaghari.pkai.data.remote.GroqApiService
) : AiProvider {
    override fun generateResponseStream(prompt: String): Flow<String> = flow {
        val apiKey = com.salmanlaghari.pkai.BuildConfig.GROQ_API_KEY
        if (apiKey.isBlank()) {
            emit("API key not configured.")
            return@flow
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
        try {
            val response = apiService.generateChatResponse("Bearer $apiKey", request)
            val fullText = response.choices?.firstOrNull()?.message?.content ?: "Empty response from Groq server."
            val words = fullText.split(" ")
            var currentText = ""
            for (i in words.indices) {
                currentText += if (i == 0) words[i] else " ${words[i]}"
                emit(currentText)
                kotlinx.coroutines.delay(80)
            }
        } catch (e: Exception) {
            emit("Error: ${e.localizedMessage ?: "Unknown network error"}")
        }
    }
}

class TogetherAiProvider(
    private val model: AiModel,
    private val apiService: com.salmanlaghari.pkai.data.remote.TogetherApiService
) : AiProvider {
    override fun generateResponseStream(prompt: String): Flow<String> = flow {
        val apiKey = com.salmanlaghari.pkai.BuildConfig.TOGETHER_API_KEY
        if (apiKey.isBlank()) {
            emit("API key not configured.")
            return@flow
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
        try {
            val response = apiService.generateChatResponse("Bearer $apiKey", request)
            val fullText = response.choices?.firstOrNull()?.message?.content ?: "Empty response from Together AI server."
            val words = fullText.split(" ")
            var currentText = ""
            for (i in words.indices) {
                currentText += if (i == 0) words[i] else " ${words[i]}"
                emit(currentText)
                kotlinx.coroutines.delay(80)
            }
        } catch (e: Exception) {
            emit("Error: ${e.localizedMessage ?: "Unknown network error"}")
        }
    }
}

class OpenAiAiProvider(
    private val model: AiModel,
    private val apiService: com.salmanlaghari.pkai.data.remote.OpenAiApiService
) : AiProvider {
    override fun generateResponseStream(prompt: String): Flow<String> = flow {
        val apiKey = com.salmanlaghari.pkai.BuildConfig.OPENAI_API_KEY
        if (apiKey.isBlank()) {
            emit("API key not configured.")
            return@flow
        }
        val modelId = when (model) {
            AiModel.CHATGPT -> "gpt-4o-mini"
            else -> "gpt-3.5-turbo"
        }
        val request = ChatCompletionRequest(
            model = modelId,
            messages = listOf(ChatMessageDto(role = "user", content = prompt)),
            stream = true
        )
        try {
            val responseBody = apiService.streamChatResponse("Bearer $apiKey", request)
            val gson = com.google.gson.Gson()
            parseSseStream(responseBody) { dataContent ->
                val chunk = gson.fromJson(dataContent, ChatCompletionChunk::class.java)
                chunk.choices?.firstOrNull()?.delta?.content
            }.collect { accumulated ->
                emit(accumulated)
            }
        } catch (e: Exception) {
            emit("Error: ${e.localizedMessage ?: "Unknown network error"}")
        }
    }
}

class CerebrasAiProvider(
    private val model: AiModel,
    private val apiService: com.salmanlaghari.pkai.data.remote.CerebrasApiService
) : AiProvider {
    override fun generateResponseStream(prompt: String): Flow<String> = flow {
        val apiKey = com.salmanlaghari.pkai.BuildConfig.CEREBRAS_API_KEY
        if (apiKey.isBlank()) {
            emit("API key not configured.")
            return@flow
        }
        val modelId = "llama3.1-8b"
        val request = ChatCompletionRequest(
            model = modelId,
            messages = listOf(ChatMessageDto(role = "user", content = prompt))
        )
        try {
            val response = apiService.generateChatResponse("Bearer $apiKey", request)
            val fullText = response.choices?.firstOrNull()?.message?.content ?: "Empty response from Cerebras server."
            val words = fullText.split(" ")
            var currentText = ""
            for (i in words.indices) {
                currentText += if (i == 0) words[i] else " ${words[i]}"
                emit(currentText)
                kotlinx.coroutines.delay(80)
            }
        } catch (e: Exception) {
            emit("Error: ${e.localizedMessage ?: "Unknown network error"}")
        }
    }
}

class SambaNovaAiProvider(
    private val model: AiModel,
    private val apiService: com.salmanlaghari.pkai.data.remote.SambaNovaApiService
) : AiProvider {
    override fun generateResponseStream(prompt: String): Flow<String> = flow {
        val apiKey = com.salmanlaghari.pkai.BuildConfig.SAMBANOVA_API_KEY
        if (apiKey.isBlank()) {
            emit("API key not configured.")
            return@flow
        }
        val modelId = "Meta-Llama-3.1-8B-Instruct"
        val request = ChatCompletionRequest(
            model = modelId,
            messages = listOf(ChatMessageDto(role = "user", content = prompt))
        )
        try {
            val response = apiService.generateChatResponse("Bearer $apiKey", request)
            val fullText = response.choices?.firstOrNull()?.message?.content ?: "Empty response from SambaNova server."
            val words = fullText.split(" ")
            var currentText = ""
            for (i in words.indices) {
                currentText += if (i == 0) words[i] else " ${words[i]}"
                emit(currentText)
                kotlinx.coroutines.delay(80)
            }
        } catch (e: Exception) {
            emit("Error: ${e.localizedMessage ?: "Unknown network error"}")
        }
    }
}
