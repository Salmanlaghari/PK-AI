package com.salmanlaghari.pkai.data.model

/**
 * The catalogue of free-tier LLM providers supported by PK AI.
 *
 * Each entry carries everything the [com.salmanlaghari.pkai.data.remote.provider.AiProviderFactory]
 * needs to construct the right network adapter:
 *  - [format]    selects the request/response adapter (OpenAI-compatible, Cloudflare or Cohere)
 *  - [baseUrl]   the REST base url (must end with `/` for Retrofit)
 *  - [defaultModel] the model id sent on every request
 *  - [apiKeyBuildConfig] the [com.salmanlaghari.pkai.BuildConfig] field that holds the API key
 *
 * API keys are NEVER hardcoded here — they are injected at build time into BuildConfig from
 * local.properties / CI secrets and read by the factory. See SECURITY notes in the PR.
 */
enum class ProviderFormat {
    OPENAI,
    COHERE
}

data class LlmProvider(
    val id: String,
    val displayName: String,
    val tagline: String,
    val logoEmoji: String,
    val format: ProviderFormat,
    val baseUrl: String,
    val defaultModel: String,
    val apiKeyBuildConfig: String,
    /**
     * Whether this provider exposes a vision-capable chat model through PK AI. When true,
     * an attached image can be sent as part of the request so the model can actually "see" it.
     */
    val supportsVision: Boolean = false,
    /**
     * The vision model id used when an image is attached (only meaningful when
     * [supportsVision] is true). Falls back to [defaultModel] when null.
     */
    val visionModel: String? = null,
    /**
     * Whether this provider can *generate* brand-new images from a text prompt. All BYOK
     * chat providers here are text-only, so this stays false for them — the real
     * image generator wired into PK AI is Pollinations' key-less image endpoint.
     */
    val supportsImageGen: Boolean = false
) {
    companion object {
        val ALL: List<LlmProvider> = listOf(
            LlmProvider(
                id = "groq",
                displayName = "Groq",
                tagline = "Fastest inference on earth",
                logoEmoji = "⚡",
                format = ProviderFormat.OPENAI,
                baseUrl = "https://api.groq.com/openai/v1/",
                // `llama-3.3-70b-versatile` was shut down by Groq on 2026-08-16 and now
                // returns HTTP 404. Groq's documented replacement is `openai/gpt-oss-120b`.
                defaultModel = "openai/gpt-oss-120b",
                apiKeyBuildConfig = "GROQ_API_KEY",
                supportsVision = true,
                visionModel = "llama-3.2-11b-vision-preview"
            ),
            LlmProvider(
                id = "llm7",
                displayName = "LLM7.io",
                tagline = "Open models, no limits",
                logoEmoji = "🔗",
                format = ProviderFormat.OPENAI,
                baseUrl = "https://api.llm7.io/v1/",
                // `deepseek-v3-0324` is no longer listed by LLM7, and `DeepSeek-V4-Flash-0731`
                // returns 503 "model temporarily busy". `mistral-Nemo-Instruct-2407` is a
                // free-tier ("turbo") model reporting 100% availability.
                defaultModel = "mistral-Nemo-Instruct-2407",
                apiKeyBuildConfig = "LLM7_API_KEY"
            ),
            LlmProvider(
                id = "mistral",
                displayName = "Mistral AI",
                tagline = "European frontier models",
                logoEmoji = "🌪️",
                format = ProviderFormat.OPENAI,
                baseUrl = "https://api.mistral.ai/v1/",
                // `mistral-medium-3-5-128b` is not a valid Mistral model id (404).
                // `mistral-small-latest` is a stable, free-tier friendly alias.
                defaultModel = "mistral-small-latest",
                apiKeyBuildConfig = "MISTRAL_API_KEY"
            ),
            LlmProvider(
                id = "cohere",
                displayName = "Cohere",
                tagline = "Enterprise-grade reasoning",
                logoEmoji = "🧩",
                format = ProviderFormat.COHERE,
                baseUrl = "https://api.cohere.com/v2/",
                // The `command-r` alias was removed by Cohere on 2025-09-15 and returns
                // HTTP 404. `command-a-03-2025` is the recommended active replacement.
                defaultModel = "command-a-03-2025",
                apiKeyBuildConfig = "COHERE_API_KEY"
            )
        )

        val DEFAULT: LlmProvider = ALL.first()

        fun fromId(id: String): LlmProvider = ALL.firstOrNull { it.id == id } ?: DEFAULT
    }
}
