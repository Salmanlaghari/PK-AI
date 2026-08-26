package com.salmanlaghari.pkai.ui.superchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salmanlaghari.pkai.data.model.ChatMessage
import com.salmanlaghari.pkai.data.remote.provider.AiProviderFactory
import com.salmanlaghari.pkai.data.remote.provider.AiResponse
import com.salmanlaghari.pkai.util.Mood
import com.salmanlaghari.pkai.util.MoodDetector
import com.salmanlaghari.pkai.util.PoseRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backing state for the Super Chat session — an avatar-companion chat with
 * mood-reactive pose changes.
 *
 * Messages live in memory only (a Super Chat is a session, not saved history).
 * Replies come from the user's default AI provider; when no provider is
 * configured (or the call fails), a friendly offline persona reply keeps the
 * session usable.
 */
@HiltViewModel
class SuperChatViewModel @Inject constructor(
    private val providerFactory: AiProviderFactory
) : ViewModel() {

    companion object {
        private const val PERSONA =
            "You are PK AI's friendly virtual assistant in Super Chat. Reply warmly, " +
                "briefly (1-2 sentences) and add one fitting emoji."
    }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _currentSticker = MutableStateFlow(PoseRegistry.defaultSticker)
    val currentSticker: StateFlow<Int> = _currentSticker.asStateFlow()

    private val _currentMood = MutableStateFlow(Mood.NEUTRAL)
    val currentMood: StateFlow<Mood> = _currentMood.asStateFlow()

    /** Sticker shown beside each message, keyed by message id. */
    private val _messageStickers = MutableStateFlow<Map<String, Int>>(emptyMap())
    val messageStickers: StateFlow<Map<String, Int>> = _messageStickers.asStateFlow()

    private val _livePoseEnabled = MutableStateFlow(true)
    val livePoseEnabled: StateFlow<Boolean> = _livePoseEnabled.asStateFlow()

    /** Sticker indices the user favorited (persisted by the fragment). */
    private val _favorites = MutableStateFlow<Set<Int>>(emptySet())
    val favorites: StateFlow<Set<Int>> = _favorites.asStateFlow()

    /** When true, use the /18+ special sticker pool instead of mood stickers. */
    private var specialMode = false

    /** Per-mood rotation counters so every message shows a different pose. */
    private val moodRotations = mutableMapOf<Mood, Int>()
    private var lastSticker: Int = PoseRegistry.defaultSticker

    fun setFavorites(favorites: Set<Int>) {
        _favorites.value = favorites
    }

    fun toggleFavorite(index: Int) {
        _favorites.value = _favorites.value.toMutableSet().apply {
            if (!add(index)) remove(index)
        }
    }

    fun setLivePoseEnabled(enabled: Boolean) {
        _livePoseEnabled.value = enabled
    }

    /** Shows a sticker chosen manually from the picker grid. */
    fun selectSticker(index: Int) {
        _currentSticker.value = index
        lastSticker = index
        _currentMood.value = Mood.NEUTRAL
    }

    /**
     * Sends a user message: appends it, switches the avatar pose to match the
     * detected mood, then streams an AI reply.
     */
    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isGenerating.value) return

        // Check for /18+ command — toggles special sticker mode
        if (trimmed.equals("/18+", ignoreCase = true)) {
            specialMode = !specialMode
            val modeMsg = ChatMessage(
                content = if (specialMode) "✨ Special sticker mode ON" else "Standard mode restored",
                isUser = false,
                timestamp = System.currentTimeMillis()
            )
            val stickerIdx = PoseRegistry.randomSpecialSticker()
            _messageStickers.value = _messageStickers.value + (modeMsg.id to stickerIdx)
            _messages.value = _messages.value + modeMsg
            return
        }

        val mood = MoodDetector.detect(trimmed)
        _currentMood.value = mood
        val pose = if (specialMode) PoseRegistry.randomSpecialSticker() else nextPoseFor(mood)
        _currentSticker.value = pose

        val userMessage = ChatMessage(
            content = trimmed,
            isUser = true,
            timestamp = System.currentTimeMillis()
        )
        _messageStickers.value = _messageStickers.value + (userMessage.id to pose)
        _messages.value = _messages.value + userMessage
        fetchReply(trimmed, specialMode)
    }

    /**
     * Picks the next pose for [mood], rotating through the mood's candidate list
     * and skipping the currently shown pose so the avatar visibly changes on
     * every message.
     */
    private fun nextPoseFor(mood: Mood): Int {
        val candidates = PoseRegistry.moodStickers[mood].orEmpty()
        if (candidates.isEmpty()) return PoseRegistry.defaultSticker
        var rotation = (moodRotations[mood] ?: -1) + 1
        var pose = PoseRegistry.stickerForMood(mood, rotation)
        // Skip forward past the currently shown pose while the list allows it.
        var guard = 0
        while (pose == lastSticker && candidates.size > 1 && guard < candidates.size) {
            rotation += 1
            pose = PoseRegistry.stickerForMood(mood, rotation)
            guard++
        }
        moodRotations[mood] = rotation
        lastSticker = pose
        return pose
    }

    private fun fetchReply(prompt: String, useSpecial: Boolean = false) {
        _isGenerating.value = true
        viewModelScope.launch {
            val reply = tryRequest(prompt) ?: offlineReply()
            val replyMessage = ChatMessage(
                content = reply,
                isUser = false,
                timestamp = System.currentTimeMillis()
            )
            // React to the reply with a fresh pose too, so every exchange
            // shows its own sticker beside the message.
            val replySticker = if (useSpecial) PoseRegistry.randomSpecialSticker()
                else nextPoseFor(_currentMood.value)
            _messageStickers.value = _messageStickers.value +
                (replyMessage.id to replySticker)
            _messages.value = _messages.value + replyMessage
            _isGenerating.value = false
        }
    }

    private suspend fun tryRequest(prompt: String): String? {
        return try {
            var text: String? = null
            providerFactory.getDefaultProvider()
                .sendMessage(prompt, emptyList())
                .collect { response ->
                    if (response is AiResponse.Success) text = response.text
                }
            text?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    /** Warm canned replies so the session never feels broken offline. */
    private fun offlineReply(): String {
        val mood = _currentMood.value
        val replies = when (mood) {
            Mood.GREETING -> listOf("Hello! 👋 How are you?", "Hi there! 👋 Great to see you!")
            Mood.HAPPY -> listOf("Very Good! 😎", "That's wonderful! 😄")
            Mood.GRATEFUL -> listOf("You're Welcome! 🥰", "Anytime! 💜")
            Mood.LOVE -> listOf("Aww, that's sweet! 💖", "Sending love right back! 💕")
            Mood.SAD -> listOf("I'm here for you 💜", "It'll be okay — stay strong! 🤗")
            Mood.FAREWELL -> listOf("Goodbye! 👋 Come back soon!", "Allah Hafiz! 👋 Take care!")
            Mood.EXCITED -> listOf("Yay! 🎉 So exciting!", "Woohoo! 🤩 Let's celebrate!")
            Mood.AGREE -> listOf("Awesome! 👍", "Great choice! 👍")
            Mood.DISAGREE -> listOf("No problem! 🙏", "Okay, we'll figure it out! 😊")
            Mood.ANGRY -> listOf("Let's take a deep breath 💜", "It's okay, I'm listening 🤗")
            Mood.THINKING -> listOf("Take your time 🤔", "No rush — think it through! 💭")
            Mood.NEUTRAL -> listOf("I'm listening! 😊", "Tell me more! ✨")
        }
        return replies.random()
    }
}
