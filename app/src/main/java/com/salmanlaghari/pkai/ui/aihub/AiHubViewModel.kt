package com.salmanlaghari.pkai.ui.aihub

import androidx.lifecycle.ViewModel
import com.salmanlaghari.pkai.data.model.AiHubModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AiHubViewModel @Inject constructor() : ViewModel() {

    private val _modelsList = MutableStateFlow<List<AiHubModel>>(emptyList())
    val modelsList: StateFlow<List<AiHubModel>> = _modelsList.asStateFlow()

    init {
        loadAiModels()
    }

    private fun loadAiModels() {
        val list = listOf(
            AiHubModel(
                id = "gemini",
                name = "Gemini",
                provider = "Google",
                emojiLogo = "💎",
                shortDesc = "Google's premium multimodal reasoning engine.",
                longDesc = "Gemini is built from the ground up to be multimodal, meaning it can generalize and seamlessly understand, operate across, and combine different types of information including text, code, images, and audio.",
                availability = "Free Access"
            ),
            AiHubModel(
                id = "claude",
                name = "Claude",
                provider = "Anthropic",
                emojiLogo = "🧠",
                shortDesc = "Anthropic's safety-first deep context reasoner.",
                longDesc = "Claude is optimized for extremely long contexts, safe system alignments, complex language translations, legal reviews, deeply structured reports, and meticulous factual correctness.",
                availability = "Premium"
            ),
            AiHubModel(
                id = "grok",
                name = "Grok",
                provider = "xAI",
                emojiLogo = "⚡",
                shortDesc = "Real-time contextual awareness with wit.",
                longDesc = "Grok is designed by xAI to answer questions with a bit of wit and has a rebellious streak, accessing real-time search signals and live streams of current global factual context.",
                availability = "Premium"
            ),
            AiHubModel(
                id = "deepseek",
                name = "DeepSeek",
                provider = "DeepSeek",
                emojiLogo = "🌊",
                shortDesc = "Ultra-fast math and technical coding powerhouse.",
                longDesc = "DeepSeek is trained on massive datasets of mathematics, computer science, programming algorithms, and structured logical puzzles, excelling at high-speed technical code synthesis.",
                availability = "Free Access"
            ),
            AiHubModel(
                id = "qwen",
                name = "Qwen",
                provider = "Alibaba",
                emojiLogo = "🐪",
                shortDesc = "East-Asia's top tier multilingual text synthesizer.",
                longDesc = "Qwen is Alibaba's top-tier foundational model, exceptionally optimized for multilingual context mapping, Asian localization, advanced math translations, and robust API tool use.",
                availability = "Free Access"
            ),
            AiHubModel(
                id = "llama",
                name = "Llama",
                provider = "Meta",
                emojiLogo = "🦙",
                shortDesc = "Meta's flagship high-performance open engine.",
                longDesc = "Llama is Meta's high-efficiency open model series, supporting exceptional logic benchmarks, conversational alignments, hardware acceleration on device, and premium text reasoning.",
                availability = "Free Access"
            ),
            AiHubModel(
                id = "mistral",
                name = "Mistral",
                provider = "Mistral AI",
                emojiLogo = "🌪️",
                shortDesc = "European high-efficiency technical logical model.",
                longDesc = "Mistral is designed with advanced attention mechanisms for small-footprint high-speed responses, offering spectacular code formatting, French localization, and structural efficiency.",
                availability = "Free Access"
            ),
            AiHubModel(
                id = "perplexity",
                name = "Perplexity",
                provider = "Perplexity",
                emojiLogo = "🔍",
                shortDesc = "Conversational real-time web search synthesizer.",
                longDesc = "Perplexity specializes in contextual real-time web searches, automatically scanning multiple sources across the live web and summarizing citations with deep logical research capabilities.",
                availability = "Premium"
            ),
            AiHubModel(
                id = "chatgpt",
                name = "ChatGPT",
                provider = "OpenAI",
                emojiLogo = "🤖",
                shortDesc = "OpenAI's benchmark text-generation wizard.",
                longDesc = "ChatGPT is a state-of-the-art language model optimized for conversational interaction, writing generation, brainstorming, programming help, and multi-turn complex logical workflows.",
                availability = "Coming Soon"
            )
        )
        _modelsList.value = list
    }

    fun toggleFavorite(modelId: String) {
        val currentList = _modelsList.value.map { model ->
            if (model.id == modelId) {
                model.copy(isFavorite = !model.isFavorite)
            } else {
                model
            }
        }
        _modelsList.value = currentList
    }
}
