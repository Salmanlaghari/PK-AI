package com.salmanlaghari.pkai.data.model

enum class AiModel(val displayName: String, val providerName: String) {
    GEMINI("Gemini", "Google"),
    CHATGPT("ChatGPT", "OpenAI"),
    CLAUDE("Claude", "Anthropic"),
    GROK("Grok", "xAI"),
    DEEPSEEK("DeepSeek", "DeepSeek"),
    QWEN("Qwen", "Alibaba"),
    LLAMA("Llama", "Meta"),
    MISTRAL("Mistral", "Mistral AI"),
    PERPLEXITY("Perplexity", "Perplexity")
}
