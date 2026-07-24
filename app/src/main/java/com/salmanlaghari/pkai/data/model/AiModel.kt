package com.salmanlaghari.pkai.data.model

enum class AiModel(val displayName: String, val providerName: String) {
    GEMINI("Gemini Ultra", "Google"),
    CLAUDE("Claude 3 Opus", "Anthropic"),
    DEEPSEEK("DeepSeek V3", "DeepSeek"),
    LLAMA("Llama 3.1 405B", "Meta"),
    MISTRAL("Mistral Large 2", "Mistral AI"),
    QWEN("Qwen 2.5 72B", "Alibaba"),
    GROK("Grok 2", "xAI"),
    PERPLEXITY("Perplexity Sonar", "Perplexity"),
    CHATGPT("ChatGPT", "OpenAI")
}
