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
    CLOUDFLARE,
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
    val needsAccountId: Boolean = false
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
                apiKeyBuildConfig = "GROQ_API_KEY"
            ),
            LlmProvider(
                id = "cloudflare",
                displayName = "Cloudflare Workers AI",
                tagline = "Edge serverless inference",
                logoEmoji = "☁️",
                format = ProviderFormat.CLOUDFLARE,
                // The service declares the full path `accounts/{accountId}/ai/run/...`, so the
                // base url must stop at /v4/ — including `accounts/` here produced
                // `/client/v4/accounts/accounts/<id>/ai/run/...` and a 7003 routing error.
                baseUrl = "https://api.cloudflare.com/client/v4/",
                defaultModel = "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
                apiKeyBuildConfig = "CLOUDFLARE_API_TOKEN",
                needsAccountId = true
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
            ),
            LlmProvider(
                id = "cerebras",
                displayName = "Cerebras",
                tagline = "Instant trillion-parameter inference",
                logoEmoji = "🚀",
                format = ProviderFormat.OPENAI,
                baseUrl = "https://api.cerebras.ai/v1/",
                // `llama3.1-70b` (404) and `llama3.1-8b` ("Model does not exist or you do not
                // have access to it") are not reachable with the current key. `gpt-oss-120b`
                // is a current production model and demonstrably exists — it returns HTTP 402
                // payment_required, i.e. the Cerebras account itself has no inference quota.
                // Keeping it here surfaces that accurate billing error instead of a misleading 404.
                defaultModel = "gpt-oss-120b",
                apiKeyBuildConfig = "CEREBRAS_API_KEY"
            ),
            LlmProvider(
                id = "huggingface",
                displayName = "Hugging Face",
                tagline = "Open models for everyone",
                logoEmoji = "🤗",
                format = ProviderFormat.OPENAI,
                baseUrl = "https://router.huggingface.co/v1/",
                // The HF router expects the fully-qualified repo id, not a slugified name.
                // `meta-llama-3-1-8b-instruct` is not a valid router model id (404).
                defaultModel = "meta-llama/Llama-3.1-8B-Instruct",
                apiKeyBuildConfig = "HUGGINGFACE_API_KEY"
            )
        )

        val DEFAULT: LlmProvider = ALL.first()

        fun fromId(id: String): LlmProvider = ALL.firstOrNull { it.id == id } ?: DEFAULT
    }
}
