package com.salmanlaghari.pkai.data.model

enum class AiModel(val displayName: String, val providerName: String, val comingSoon: Boolean = false) {
    GEMINI("Gemini", "Google"),
    CHATGPT("ChatGPT", "OpenAI"),
    CLAUDE("Claude", "Anthropic"),
    GROK("Grok", "xAI"),
    DEEPSEEK("DeepSeek", "DeepSeek"),
    QWEN("Qwen", "Alibaba"),
    LLAMA("Llama", "Meta", comingSoon = true),
    MISTRAL("Mistral", "Mistral AI"),
    PERPLEXITY("Perplexity", "Perplexity", comingSoon = true),
    WEB("PK AI Web", "OpenRouter")
}
