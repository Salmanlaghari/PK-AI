package com.salmanlaghari.pkai.data.remote.provider

import com.salmanlaghari.pkai.data.model.ChatMessage
import com.salmanlaghari.pkai.data.model.LlmProvider
import com.salmanlaghari.pkai.BuildConfig
import android.util.Log
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
    /**
     * @param imageDataUri optional `data:image/…;base64,…` payload the user attached.
     * Only providers with [com.salmanlaghari.pkai.data.model.LlmProvider.supportsVision]
     * will actually inspect it; others treat the call as a normal text request.
     */
    fun sendMessage(
        prompt: String,
        history: List<ChatMessage>,
        imageDataUri: String? = null
    ): Flow<AiResponse>
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

private const val LOG_TAG = "PkAiProvider"

/**
 * Reads the error body of a failed call exactly once.
 *
 * [okhttp3.ResponseBody.string] may only be consumed a single time, so the result is
 * threaded through both the Logcat output and the user-facing message.
 */
fun errorBodyOf(e: HttpException): String? = try {
    e.response()?.errorBody()?.string()?.takeIf { it.isNotBlank() }
} catch (t: Throwable) {
    null
}

/**
 * Debug-build diagnostics: prints the exact failing URL, status code and raw response body
 * for any non-2xx response so provider issues (deprecated model ids, wrong paths, bad
 * tokens) can be diagnosed straight from Logcat.
 *
 * Wrapped in a try/catch because `android.util.Log` is not available on the plain JVM used
 * by unit tests.
 */
fun logHttpFailure(providerName: String, e: HttpException, errorBody: String?) {
    if (!BuildConfig.DEBUG) return
    val url = e.response()?.raw()?.request?.url?.toString() ?: "<unknown url>"
    val message = buildString {
        append("✗ $providerName failed\n")
        append("   HTTP  : ${e.code()} ${e.message()}\n")
        append("   URL   : $url\n")
        append("   BODY  : ${errorBody ?: "<empty>"}")
    }
    try {
        Log.e(LOG_TAG, message)
    } catch (t: Throwable) {
        println(message)
    }
}

/** Trims a provider error body down to something short enough to show in a chat bubble. */
private fun shortDetail(errorBody: String?): String =
    errorBody?.replace(Regex("\\s+"), " ")?.trim()?.take(300)?.let { " — $it" } ?: ""

/** Maps an HTTP status code to a clear, actionable in-app message. */
fun mapHttpError(providerName: String, code: Int, message: String?, errorBody: String? = null): String = when (code) {
    401, 403 -> "$providerName: Authentication failed (HTTP $code). Your API key or token is invalid, " +
        "or lacks the permission needed for inference.${shortDetail(errorBody)}"
    402 -> "$providerName: Payment or quota required (HTTP 402). This account has no remaining " +
        "inference quota.${shortDetail(errorBody)}"
    404 -> "$providerName: Model or endpoint not found (HTTP 404). The configured model id is most likely " +
        "deprecated or renamed by the provider.${shortDetail(errorBody)}"
    429 -> "$providerName: Rate limit exceeded (HTTP 429). Please wait and try again.${shortDetail(errorBody)}"
    in 500..599 -> "$providerName: The provider's servers are unavailable (HTTP $code). Try again later."
    else -> "$providerName: Request failed (HTTP $code)${message?.let { " — $it" } ?: ""}${shortDetail(errorBody)}"
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

    override fun sendMessage(
        prompt: String,
        history: List<ChatMessage>,
        imageDataUri: String?
    ): Flow<AiResponse> = flow {
        if (apiKey.isBlank()) {
            emit(AiResponse.Error(missingKeyMessage()))
            return@flow
        }

        // When the user attached an image and this provider can see, switch to its
        // vision model and send the picture as a multimodal content part.
        val useVision = imageDataUri != null && provider.supportsVision
        val model = if (useVision) provider.visionModel ?: provider.defaultModel else provider.defaultModel

        val historyMessages = history.map { ChatMessageDto(roleOf(it), it.content) }
        val userMessage = if (useVision) {
            ChatMessageDto(
                role = "user",
                content = listOf(
                    mapOf("type" to "text", "text" to prompt),
                    mapOf("type" to "image_url", "image_url" to mapOf("url" to imageDataUri!!))
                )
            )
        } else {
            ChatMessageDto(role = "user", content = prompt)
        }
        val messages = historyMessages + userMessage
        val request = ChatCompletionRequest(model = model, messages = messages)
        try {
            val response = service.generateChatResponse("Bearer $apiKey", request)
            val raw = response.choices.firstOrNull()?.message?.content
            val text = if (raw is String) raw else raw?.toString()
            if (text.isNullOrBlank()) {
                emit(AiResponse.Error("${provider.displayName} returned an empty response. Please try again."))
            } else {
                emit(AiResponse.Success(text))
            }
        } catch (e: HttpException) {
            val body = errorBodyOf(e)
            logHttpFailure(provider.displayName, e, body)
            emit(AiResponse.Error(mapHttpError(provider.displayName, e.code(), e.message(), body)))
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

    override fun sendMessage(
        prompt: String,
        history: List<ChatMessage>,
        imageDataUri: String?
    ): Flow<AiResponse> = flow {
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
            val body = errorBodyOf(e)
            logHttpFailure("Cloudflare Workers AI", e, body)
            emit(AiResponse.Error(mapHttpError("Cloudflare Workers AI", e.code(), e.message(), body)))
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

    override fun sendMessage(
        prompt: String,
        history: List<ChatMessage>,
        imageDataUri: String?
    ): Flow<AiResponse> = flow {
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
            val body = errorBodyOf(e)
            logHttpFailure("Cohere", e, body)
            emit(AiResponse.Error(mapHttpError("Cohere", e.code(), e.message(), body)))
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
 * Key-less free LLM for the Home "Free AI" tab
 *
 * This is the second Free-tier model (alongside [PublicFreeAiProvider]) and, unlike the
 * public fact/advice chatbot, it is a genuine LLM conversation that still needs no API key,
 * no account and no billing details.
 *
 * Two independent key-less upstreams are tried in order because each throttles or bills
 * certain source IP ranges (Pollinations charges datacenter IPs; LLM7 rate-limits bursts).
 * Chaining them keeps the free tier usable from any network.
 * ───────────────────────────────────────────────────────────────────────────── */

class KeylessLlmAiProvider(
    private val pollinations: PollinationsApiService,
    private val llm7: OpenAiCompatibleApiService
) : AiProvider {

    private companion object {
        const val DISPLAY_NAME = "PK AI Free LLM"
        /** Keep the prompt well inside the practical URL length limit. */
        const val MAX_PROMPT_CHARS = 1800
        const val HISTORY_TURNS = 6
        /** Pollinations' default key-less text model. */
        const val POLLINATIONS_MODEL = "openai"
        /** A free-tier LLM7 model that accepts anonymous requests. */
        const val LLM7_MODEL = "mistral-Nemo-Instruct-2407"
    }

    override fun sendMessage(
        prompt: String,
        history: List<ChatMessage>,
        imageDataUri: String?
    ): Flow<AiResponse> = flow {
        val attempted = mutableListOf<String>()

        val pollinationsText = tryPollinations(prompt, history)
        if (pollinationsText != null) {
            emit(AiResponse.Success(pollinationsText))
            return@flow
        }
        attempted.add("Pollinations")

        val llm7Text = tryLlm7(prompt, history)
        if (llm7Text != null) {
            emit(AiResponse.Success(llm7Text))
            return@flow
        }
        attempted.add("LLM7 (anonymous)")

        emit(
            AiResponse.Error(
                "$DISPLAY_NAME: every key-less endpoint is currently unavailable " +
                    "(tried ${attempted.joinToString(", ")}). Please try again, or pick a " +
                    "provider in Settings → AI."
            )
        )
    }

    /** Pollinations' anonymous plain-text completion endpoint. */
    private suspend fun tryPollinations(prompt: String, history: List<ChatMessage>): String? = try {
        pollinations.generate(
            prompt = buildPrompt(prompt, history),
            model = POLLINATIONS_MODEL,
            isPrivate = true
        ).string().trim().takeIf { it.isNotBlank() }
    } catch (e: HttpException) {
        logHttpFailure("$DISPLAY_NAME → Pollinations", e, errorBodyOf(e))
        null
    } catch (e: Exception) {
        null
    }

    /** LLM7.io accepts anonymous OpenAI-compatible requests, so no key is needed. */
    private suspend fun tryLlm7(prompt: String, history: List<ChatMessage>): String? = try {
        val messages = history.takeLast(HISTORY_TURNS)
            .map { ChatMessageDto(roleOf(it), it.content) } +
            listOf(ChatMessageDto("user", prompt))
        llm7.generateChatResponse(
            "Bearer unused",
            ChatCompletionRequest(model = LLM7_MODEL, messages = messages)
        ).choices.firstOrNull()?.message?.content?.trim()?.takeIf { it.isNotBlank() }
    } catch (e: HttpException) {
        logHttpFailure("$DISPLAY_NAME → LLM7", e, errorBodyOf(e))
        null
    } catch (e: Exception) {
        null
    }

    /**
     * Pollinations' key-less endpoint takes a single prompt string, so recent turns are
     * folded into the prompt to preserve conversational context.
     */
    private fun buildPrompt(prompt: String, history: List<ChatMessage>): String {
        val recent = history.takeLast(HISTORY_TURNS)
        val conversation = buildString {
            append("You are PK AI, a helpful assistant. Reply conversationally in plain text.\n\n")
            recent.forEach { message ->
                append(if (message.isUser) "User: " else "Assistant: ")
                append(message.content.trim())
                append("\n")
            }
            append("User: ")
            append(prompt.trim())
            append("\nAssistant:")
        }
        return conversation.takeLast(MAX_PROMPT_CHARS)
    }
}

/* ─────────────────────────────────────────────────────────────────────────────
 * Public Free provider — no API key required (kept for the Free AI tab)
 * ───────────────────────────────────────────────────────────────────────────── */

class PublicFreeAiProvider(
    private val apiService: PublicFreeApiService
) : AiProvider {
    override fun sendMessage(
        prompt: String,
        history: List<ChatMessage>,
        imageDataUri: String?
    ): Flow<AiResponse> = flow {
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
