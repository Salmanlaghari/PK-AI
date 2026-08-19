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
                defaultModel = "llama-3.3-70b-versatile",
                apiKeyBuildConfig = "GROQ_API_KEY"
            ),
            LlmProvider(
                id = "cloudflare",
                displayName = "Cloudflare Workers AI",
                tagline = "Edge serverless inference",
                logoEmoji = "☁️",
                format = ProviderFormat.CLOUDFLARE,
                baseUrl = "https://api.cloudflare.com/client/v4/accounts/",
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
                defaultModel = "deepseek-v3-0324",
                apiKeyBuildConfig = "LLM7_API_KEY"
            ),
            LlmProvider(
                id = "mistral",
                displayName = "Mistral AI",
                tagline = "European frontier models",
                logoEmoji = "🌪️",
                format = ProviderFormat.OPENAI,
                baseUrl = "https://api.mistral.ai/v1/",
                defaultModel = "mistral-medium-3-5-128b",
                apiKeyBuildConfig = "MISTRAL_API_KEY"
            ),
            LlmProvider(
                id = "cohere",
                displayName = "Cohere",
                tagline = "Enterprise-grade reasoning",
                logoEmoji = "🧩",
                format = ProviderFormat.COHERE,
                baseUrl = "https://api.cohere.com/v2/",
                defaultModel = "command-r",
                apiKeyBuildConfig = "COHERE_API_KEY"
            ),
            LlmProvider(
                id = "cerebras",
                displayName = "Cerebras",
                tagline = "Instant trillion-parameter inference",
                logoEmoji = "🚀",
                format = ProviderFormat.OPENAI,
                baseUrl = "https://api.cerebras.ai/v1/",
                defaultModel = "llama3.1-70b",
                apiKeyBuildConfig = "CEREBRAS_API_KEY"
            ),
            LlmProvider(
                id = "huggingface",
                displayName = "Hugging Face",
                tagline = "Open models for everyone",
                logoEmoji = "🤗",
                format = ProviderFormat.OPENAI,
                baseUrl = "https://router.huggingface.co/v1/",
                defaultModel = "meta-llama-3-1-8b-instruct",
                apiKeyBuildConfig = "HUGGINGFACE_API_KEY"
            )
        )

        val DEFAULT: LlmProvider = ALL.first()

        fun fromId(id: String): LlmProvider = ALL.firstOrNull { it.id == id } ?: DEFAULT
    }
}
