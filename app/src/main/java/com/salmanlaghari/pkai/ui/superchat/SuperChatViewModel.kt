package com.salmanlaghari.pkai.ui.superchat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salmanlaghari.pkai.data.model.ChatMessage
import com.salmanlaghari.pkai.data.remote.provider.AiProviderFactory
import com.salmanlaghari.pkai.data.remote.provider.AiResponse
import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import com.salmanlaghari.pkai.data.repository.ChatHistoryRecorder
import kotlinx.coroutines.flow.first
import com.salmanlaghari.pkai.util.PkAiAssistant
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
 * mood-reactive pose changes and integrated PK AI Smart Calendar Assistant.
 *
 * Messages live in memory only (a Super Chat is a session, not saved history).
 * Replies come from the user's default AI provider; when no provider is
 * configured (or the call fails), a friendly offline persona reply keeps the
 * session usable.
 */
@HiltViewModel
class SuperChatViewModel @Inject constructor(
    private val providerFactory: AiProviderFactory,
    private val preferencesManager: PreferencesManager,
    private val chatHistoryRecorder: ChatHistoryRecorder
) : ViewModel() {

    companion object {
        private const val TAG = "SuperChatViewModel"
        private const val PERSONA =
            "You are PK AI's friendly virtual assistant in Super Chat. Reply warmly, " +
                "briefly (1-2 sentences) and add one fitting emoji."
    }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isPkAiMode = MutableStateFlow(true)
    val isPkAiMode: StateFlow<Boolean> = _isPkAiMode.asStateFlow()

    private val _currentSticker = MutableStateFlow(PoseRegistry.defaultSticker)
    val currentSticker: StateFlow<Int> = _currentSticker.asStateFlow()

    private val _currentMood = MutableStateFlow(Mood.NEUTRAL)
    val currentMood: StateFlow<Mood> = _currentMood.asStateFlow()

    /** Sticker shown beside each message, keyed by message id. */
    private val _messageStickers = MutableStateFlow<Map<String, Int>>(emptyMap())
    val messageStickers: StateFlow<Map<String, Int>> = _messageStickers.asStateFlow()

    /** Session-level history tracking (History screen). Null until the first AI reply. */
    private var historySessionId: String? = null
    private var historySessionTitle: String? = null

    private val _livePoseEnabled = MutableStateFlow(true)
    val livePoseEnabled: StateFlow<Boolean> = _livePoseEnabled.asStateFlow()

    /** Sticker indices the user favorited (persisted by the fragment). */
    private val _favorites = MutableStateFlow<Set<Int>>(emptySet())
    val favorites: StateFlow<Set<Int>> = _favorites.asStateFlow()

    /** When true, use the /18+ special sticker pool instead of mood stickers. */
    private var specialMode = false
    private var lastLanguageInstruction: String =
        com.salmanlaghari.pkai.util.LanguageDetector.Lang.ENGLISH.instruction

    /** Per-mood rotation counters so every message shows a different pose. */
    private val moodRotations = mutableMapOf<Mood, Int>()
    private var lastSticker: Int = PoseRegistry.defaultSticker

    fun togglePkAiMode() {
        _isPkAiMode.value = !_isPkAiMode.value
    }

    fun setPkAiMode(enabled: Boolean) {
        _isPkAiMode.value = enabled
    }

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
     *
     * Song requests ("play kesariya") are routed to PagalWorld search and come
     * back as a visual song card.
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

        // Keep the language from the last real user text for attachment replies.
        lastLanguageInstruction =
            com.salmanlaghari.pkai.util.LanguageDetector.detect(trimmed).instruction

        // Song search intent → visual song card via PagalWorld.
        // Guarded by _isGenerating so rapid taps can't stack searches.
        val songQuery = com.salmanlaghari.pkai.util.SongSearchHelper.extractSongQuery(trimmed)
        if (songQuery != null && !_isGenerating.value) {
            searchAndSendSong(trimmed, songQuery)
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
        fetchReply(trimmed, specialMode, languageInstruction = lastLanguageInstruction)
    }

    /** Sends an image attachment as a user message, then fetches an AI reply. */
    fun sendImageMessage(uri: String, imageDataUri: String? = null, displayName: String? = null) {
        if (_isGenerating.value) return
        if (imageDataUri.isNullOrBlank()) {
            _messages.value = _messages.value + ChatMessage(
                content = "⚠️ Image read nahi ho saki (unsupported format ya 4 MB se bari). Image analyse nahi hui.",
                isUser = false,
                timestamp = System.currentTimeMillis()
            )
            return
        }
        val userMessage = ChatMessage(
            content = "📷 Image",
            isUser = true,
            timestamp = System.currentTimeMillis(),
            attachmentType = "image",
            attachmentUri = uri,
            attachmentName = displayName
        )
        _currentMood.value = Mood.NEUTRAL
        val pose = if (specialMode) PoseRegistry.randomSpecialSticker()
            else nextPoseFor(Mood.NEUTRAL)
        _currentSticker.value = pose
        _messageStickers.value = _messageStickers.value + (userMessage.id to pose)
        _messages.value = _messages.value + userMessage
        fetchReply(
            "The user shared an image with me. Describe only what you can actually see.",
            specialMode,
            imageDataUri,
            languageInstruction = lastLanguageInstruction
        )
    }

    /** Sends a voice note as a user message, then fetches an AI reply. */
    fun sendVoiceMessage(uri: String, durationLabel: String) {
        if (_isGenerating.value) return
        val userMessage = ChatMessage(
            content = "🎤 Voice note",
            isUser = true,
            timestamp = System.currentTimeMillis(),
            attachmentType = "audio",
            attachmentUri = uri,
            attachmentName = durationLabel
        )
        _currentMood.value = Mood.NEUTRAL
        val pose = if (specialMode) PoseRegistry.randomSpecialSticker()
            else nextPoseFor(Mood.NEUTRAL)
        _currentSticker.value = pose
        _messageStickers.value = _messageStickers.value + (userMessage.id to pose)
        _messages.value = _messages.value + userMessage
        fetchReply(
            "The user sent a voice note, but its audio has not been transcribed. Do not pretend to hear it; ask the user to type the request if needed.",
            specialMode,
            languageInstruction = lastLanguageInstruction
        )
    }

    /**
     * Song search: appends the user message, searches PagalWorld, and posts a
     * visual song card (artwork + title + artist + streamable audio).
     * Song data is packed into the attachment fields as:
     * attachmentType="song", attachmentUri=audioUrl,
     * attachmentName="title|||artist|||artworkUrl".
     */
    fun searchAndSendSong(originalText: String, query: String) {
        if (_isGenerating.value) return
        val userMessage = ChatMessage(
            content = originalText,
            isUser = true,
            timestamp = System.currentTimeMillis()
        )
        val pose = if (specialMode) PoseRegistry.randomSpecialSticker()
            else nextPoseFor(MoodDetector.detect(originalText))
        _currentSticker.value = pose
        _messageStickers.value = _messageStickers.value + (userMessage.id to pose)
        _messages.value = _messages.value + userMessage

        _isGenerating.value = true
        viewModelScope.launch {
            val song = com.salmanlaghari.pkai.util.SongSearchHelper.searchSong(query)
            val replyMessage = if (song != null) {
                ChatMessage(
                    content = "🎵 Ye raha aapka song:",
                    isUser = false,
                    modelUsed = "Song Search",
                    timestamp = System.currentTimeMillis(),
                    attachmentType = "song",
                    attachmentUri = song.audioUrl,
                    attachmentName = "${song.title}|||${song.artist}|||${song.artworkUrl}"
                )
            } else {
                ChatMessage(
                    content = "😔 \"$query\" nahi mila. Koi aur song try karein!",
                    isUser = false,
                    timestamp = System.currentTimeMillis()
                )
            }
            val sticker = if (specialMode) PoseRegistry.randomSpecialSticker()
                else nextPoseFor(Mood.HAPPY)
            _messageStickers.value = _messageStickers.value + (replyMessage.id to sticker)
            _messages.value = _messages.value + replyMessage
            _isGenerating.value = false
        }
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

    private fun fetchReply(
        prompt: String,
        useSpecial: Boolean = false,
        imageDataUri: String? = null,
        languageInstruction: String? = null
    ) {
        _isGenerating.value = true
        viewModelScope.launch {
            val reply = tryRequest(prompt, imageDataUri, languageInstruction)
                ?: if (!imageDataUri.isNullOrBlank()) {
                    "⚠️ Is image ko analyse nahi kiya ja saka — configured vision provider unavailable hai."
                } else offlineReply()
            val replyMessage = ChatMessage(
                content = reply,
                isUser = false,
                modelUsed = if (_isPkAiMode.value) PkAiAssistant.PK_AI_LABEL else "Super Chat",
                timestamp = System.currentTimeMillis()
            )
            // React to the reply with a fresh pose too, so every exchange
            // shows its own sticker beside the message.
            val replySticker = if (useSpecial) PoseRegistry.randomSpecialSticker()
                else nextPoseFor(_currentMood.value)
            _messageStickers.value = _messageStickers.value +
                (replyMessage.id to replySticker)
            _messages.value = _messages.value + replyMessage
            // Record/refresh this Super Chat session in the History screen.
            if (historySessionTitle == null) {
                historySessionTitle = prompt.trim().take(60)
            }
            // Best-effort: a Room failure here must never crash this coroutine
            // or skip the _isGenerating reset below — history is secondary.
            historySessionId = runCatching {
                chatHistoryRecorder.recordSession(
                    sessionId = historySessionId,
                    title = historySessionTitle ?: prompt.trim().take(60),
                    preview = reply.take(120)
                )
            }.getOrElse { e ->
                Log.w(TAG, "History recording failed (best-effort)", e)
                historySessionId
            }
            _isGenerating.value = false
        }
    }

    private suspend fun tryRequest(
        prompt: String,
        imageDataUri: String? = null,
        languageInstruction: String? = null
    ): String? {
        val instruction = languageInstruction
            ?: com.salmanlaghari.pkai.util.LanguageDetector.detect(prompt).instruction
        val finalPrompt = if (_isPkAiMode.value) {
            PkAiAssistant.buildPkAiPrompt(prompt, instruction)
        } else {
            "$prompt\n[Reply in $instruction and be precise; if unsure, say so.]"
        }
        val selectedProviderId = preferencesManager.selectedProviderId.first()
        val activeProviderMeta = providerFactory.defaultProviderId(selectedProviderId)
            ?.let { id -> com.salmanlaghari.pkai.data.model.LlmProvider.fromId(id) }
        // A vision request must use the exact provider resolved for the request.
        if (!imageDataUri.isNullOrBlank() && activeProviderMeta?.supportsVision != true) return null
        val activeProvider = providerFactory.getDefaultProvider(selectedProviderId)
        try {
            var text: String? = null
            activeProvider
                .sendMessage(finalPrompt, emptyList(), imageDataUri)
                .collect { response ->
                    if (response is AiResponse.Success) text = response.text
                }
            if (!text.isNullOrBlank()) return text
        } catch (_: Exception) {
        }

        // A keyless/text-only fallback must never fabricate an image description.
        if (!imageDataUri.isNullOrBlank()) return null

        // 2. Try free provider fallback
        try {
            var freeText: String? = null
            providerFactory.getFreeProvider("pollinations_p1")
                .sendMessage(finalPrompt, emptyList())
                .collect { response ->
                    if (response is AiResponse.Success) freeText = response.text
                }
            if (!freeText.isNullOrBlank()) return freeText
        } catch (_: Exception) {
        }

        // 3. Fallback for PK AI smart schedule if offline
        if (_isPkAiMode.value) {
            if (PkAiAssistant.isSchedulingQuery(prompt)) {
                return PkAiAssistant.formatPkAiSmartPlan(prompt)
            }
        }
        return null
    }

    /** Warm canned replies so the session never feels broken offline. */
    private fun offlineReply(): String {
        if (_isPkAiMode.value) {
            return "✨ PK AI Assistant: I'm here to help you organize your schedule, meetings, reminders, and daily productivity. What would you like to plan today?"
        }
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
