package com.salmanlaghari.pkai.data.model

enum class AiModel(val displayName: String, val providerName: String) {
    GEMINI("Gemini", "Google"),
    CLAUDE("Claude", "Anthropic"),
    GROK("Grok", "xAI"),
    DEEPSEEK("DeepSeek", "DeepSeek"),
    QWEN("Qwen", "Alibaba"),
    LLAMA("Llama", "Meta"),
    MISTRAL("Mistral", "Mistral AI"),
    PERPLEXITY("Perplexity", "Perplexity"),
    CHATGPT("ChatGPT", "OpenAI")
}
