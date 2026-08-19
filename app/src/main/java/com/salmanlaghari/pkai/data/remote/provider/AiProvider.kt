package com.salmanlaghari.pkai.data.remote.provider

import com.salmanlaghari.pkai.data.model.ChatMessage
import com.salmanlaghari.pkai.data.model.LlmProvider
import com.salmanlaghari.pkai.data.remote.ChatMessageDto
import com.salmanlaghari.pkai.data.remote.ChatCompletionRequest
import com.salmanlaghari.pkai.data.remote.PublicFreeApiService
import com.google.gson.JsonElement
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import java.io.IOException

/**
 * Common contract every LLM backend implements.
 *
 * [sendMessage] is intentionally a cold [Flow] so call sites can stream partial
 * progress / a single terminal result and always receive a structured [AiResponse]
 * (success or a user-friendly error) instead of throwing.
 */
interface AiProvider {
    fun sendMessage(prompt: String, history: List<ChatMessage>): Flow<AiResponse>
}

sealed interface AiResponse {
    val text: String
    data class Success(override val text: String) : AiResponse
    data class Error(override val text: String) : AiResponse
}

/* ─────────────────────────────────────────────────────────────────────────────
 * Shared helpers
 * ───────────────────────────────────────────────────────────────────────────── */

private fun roleOf(message: ChatMessage): String = if (message.isUser) "user" else "assistant"

/** Maps an HTTP status code to a clear, actionable in-app message. */
fun mapHttpError(providerName: String, code: Int, message: String?): String = when (code) {
    401, 403 -> "$providerName: Authentication failed (HTTP $code). Verify your API key."
    429 -> "$providerName: Rate limit exceeded (HTTP 429). Please wait and try again."
    in 500..599 -> "$providerName: The provider's servers are unavailable (HTTP $code). Try again later."
    else -> "$providerName: Request failed (HTTP $code)${message?.let { " — $it" } ?: ""}"
}

/* ─────────────────────────────────────────────────────────────────────────────
 * OpenAI-compatible provider (Groq, LLM7.io, Mistral, Cerebras, Hugging Face)
 * All five providers share this single adapter, configured per instance.
 * ───────────────────────────────────────────────────────────────────────────── */

open class OpenAiCompatibleProvider(
    protected val provider: LlmProvider,
    private val apiKey: String,
    private val service: OpenAiCompatibleApiService
) : AiProvider {

    override fun sendMessage(prompt: String, history: List<ChatMessage>): Flow<AiResponse> = flow {
        if (apiKey.isBlank()) {
            emit(AiResponse.Error(missingKeyMessage()))
            return@flow
        }
        val messages = history.map { ChatMessageDto(roleOf(it), it.content) } +
            listOf(ChatMessageDto("user", prompt))
        val request = ChatCompletionRequest(model = provider.defaultModel, messages = messages)
        try {
            val response = service.generateChatResponse("Bearer $apiKey", request)
            val text = response.choices.firstOrNull()?.message?.content
            if (text.isNullOrBlank()) {
                emit(AiResponse.Error("${provider.displayName} returned an empty response. Please try again."))
            } else {
                emit(AiResponse.Success(text))
            }
        } catch (e: HttpException) {
            emit(AiResponse.Error(mapHttpError(provider.displayName, e.code(), e.message())))
        } catch (e: IOException) {
            emit(AiResponse.Error("${provider.displayName}: Network error. Check your internet connection."))
        } catch (e: Exception) {
            emit(AiResponse.Error("${provider.displayName}: ${e.localizedMessage ?: "Unknown error"}"))
        }
    }

    protected fun missingKeyMessage(): String =
        "${provider.displayName} API key not configured. Add ${provider.apiKeyBuildConfig} to local.properties."
}

/* ─────────────────────────────────────────────────────────────────────────────
 * Cloudflare Workers AI — custom request/response adapter
 * ───────────────────────────────────────────────────────────────────────────── */

class CloudflareWorkersAiProvider(
    private val provider: LlmProvider,
    private val apiToken: String,
    private val accountId: String,
    private val service: CloudflareWorkersApiService
) : AiProvider {

    override fun sendMessage(prompt: String, history: List<ChatMessage>): Flow<AiResponse> = flow {
        if (apiToken.isBlank() || accountId.isBlank()) {
            emit(AiResponse.Error("Cloudflare API token or account id not configured. Add CLOUDFLARE_API_TOKEN and CLOUDFLARE_ACCOUNT_ID to local.properties."))
            return@flow
        }
        val messages = history.map { CloudflareMessage(roleOf(it), it.content) } +
            listOf(CloudflareMessage("user", prompt))
        try {
            val response = service.run(
                accountId = accountId,
                modelPath = provider.defaultModel,
                authorization = "Bearer $apiToken",
                request = CloudflareRequest(messages)
            )
            val text = response.result?.response
            if (text.isNullOrBlank()) {
                emit(AiResponse.Error("Cloudflare Workers AI returned an empty response. Please try again."))
            } else {
                emit(AiResponse.Success(text))
            }
        } catch (e: HttpException) {
            emit(AiResponse.Error(mapHttpError("Cloudflare Workers AI", e.code(), e.message())))
        } catch (e: IOException) {
            emit(AiResponse.Error("Cloudflare Workers AI: Network error. Check your internet connection."))
        } catch (e: Exception) {
            emit(AiResponse.Error("Cloudflare Workers AI: ${e.localizedMessage ?: "Unknown error"}"))
        }
    }
}

/* ─────────────────────────────────────────────────────────────────────────────
 * Cohere — v2 chat API custom adapter
 * ───────────────────────────────────────────────────────────────────────────── */

class CohereAiProvider(
    private val provider: LlmProvider,
    private val apiKey: String,
    private val service: CohereApiService
) : AiProvider {

    override fun sendMessage(prompt: String, history: List<ChatMessage>): Flow<AiResponse> = flow {
        if (apiKey.isBlank()) {
            emit(AiResponse.Error("Cohere API key not configured. Add COHERE_API_KEY to local.properties."))
            return@flow
        }
        val messages = history.map { CohereMessage(roleOf(it), it.content) } +
            listOf(CohereMessage("user", prompt))
        try {
            val response = service.chat(
                authorization = "Bearer $apiKey",
                request = CohereRequest(model = provider.defaultModel, messages = messages)
            )
            val text = extractCohereText(response)
            if (text.isNullOrBlank()) {
                emit(AiResponse.Error("Cohere returned an empty response. Please try again."))
            } else {
                emit(AiResponse.Success(text))
            }
        } catch (e: HttpException) {
            emit(AiResponse.Error(mapHttpError("Cohere", e.code(), e.message())))
        } catch (e: IOException) {
            emit(AiResponse.Error("Cohere: Network error. Check your internet connection."))
        } catch (e: Exception) {
            emit(AiResponse.Error("Cohere: ${e.localizedMessage ?: "Unknown error"}"))
        }
    }

    private fun extractCohereText(response: CohereResponse): String? {
        val content: JsonElement = response.message?.content ?: return null
        return try {
            if (content.isJsonArray) {
                content.asJsonArray.joinToString("") { el ->
                    el.asJsonObject.get("text")?.asString ?: ""
                }
            } else {
                content.asString
            }
        } catch (e: Exception) {
            null
        }
    }
}

/* ─────────────────────────────────────────────────────────────────────────────
 * Public Free provider — no API key required (kept for the Free AI tab)
 * ───────────────────────────────────────────────────────────────────────────── */

class PublicFreeAiProvider(
    private val apiService: PublicFreeApiService
) : AiProvider {
    override fun sendMessage(prompt: String, history: List<ChatMessage>): Flow<AiResponse> = flow {
        delay(1000) // Premium conversational pacing
        val lowerPrompt = prompt.lowercase()
        try {
            val reply = if (lowerPrompt.contains("fact") || lowerPrompt.contains("know") || lowerPrompt.contains("something")) {
                val response = apiService.getFreeFact()
                "Here is an interesting public fact for you:\n\n${response["fact"] ?: "AI is the future!"}"
            } else if (lowerPrompt.contains("advice") || lowerPrompt.contains("help") || lowerPrompt.contains("suggest")) {
                val response = apiService.getFreeAdvice()
                val slip = response["slip"] as? Map<*, *>
                slip?.get("advice") as? String ?: "Stay positive and keep coding!"
            } else {
                val words = prompt.trim().split("\\s+".toRegex()).size
                "I am PK AI's **Free Public Chatbot** (No API Key required)!\n\nI processed your query (\"$prompt\") containing $words words using unauthenticated public endpoints. To experience ultra-fast reasoning with PK AI, please pick a provider in **Settings → AI**!"
            }
            emit(AiResponse.Success(reply))
        } catch (e: Exception) {
            emit(
                AiResponse.Success(
                    "I am PK AI's **Free Public Chatbot**!\n\nYour query: \"$prompt\"\n\n" +
                        "I am currently operating in smart offline mode. Switch to the **Premium Chat** tab and choose a provider in Settings to utilize PK AI!"
                )
            )
        }
    }
}
