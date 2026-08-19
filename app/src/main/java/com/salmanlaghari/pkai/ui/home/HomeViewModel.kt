package com.salmanlaghari.pkai.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import com.salmanlaghari.pkai.data.local.room.ChatMessageDao
import com.salmanlaghari.pkai.data.model.ChatMessage
import com.salmanlaghari.pkai.data.model.FreeAiModel
import com.salmanlaghari.pkai.data.model.LlmProvider
import com.salmanlaghari.pkai.data.remote.provider.AiProvider
import com.salmanlaghari.pkai.data.remote.provider.AiProviderFactory
import com.salmanlaghari.pkai.data.remote.provider.AiResponse
import com.salmanlaghari.pkai.data.repository.AppRepository
import com.salmanlaghari.pkai.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val authRepository: AuthRepository,
    private val chatMessageDao: ChatMessageDao,
    private val aiProviderFactory: AiProviderFactory,
    private val preferencesManager: PreferencesManager,
    private val okHttpClient: OkHttpClient
) : ViewModel() {

    private val _isFreeMode = MutableStateFlow(false)
    val isFreeMode: StateFlow<Boolean> = _isFreeMode.asStateFlow()

    val chatMessages: StateFlow<List<ChatMessage>> = chatMessageDao.getAllMessagesFlow()
        .combine(_isFreeMode) { messages, freeMode ->
            // Free-tier messages are tagged with a "Free…" label so the two tabs keep
            // separate conversations (legacy "Free Public AI" history still matches).
            if (freeMode) {
                messages.filter { FreeAiModel.isFreeLabel(it.modelUsed) }
            } else {
                messages.filter { !FreeAiModel.isFreeLabel(it.modelUsed) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** The provider id the user selected in Settings (defaults to Groq). */
    private val _selectedProviderId = MutableStateFlow(LlmProvider.DEFAULT.id)
    val selectedProvider: StateFlow<LlmProvider> = _selectedProviderId
        .map { LlmProvider.fromId(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LlmProvider.DEFAULT
        )

    /** The key-less model the user picked in the Free AI tab (defaults to the free LLM). */
    private val _selectedFreeModelId = MutableStateFlow(FreeAiModel.DEFAULT.id)
    val selectedFreeModel: StateFlow<FreeAiModel> = _selectedFreeModelId
        .map { FreeAiModel.fromId(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FreeAiModel.DEFAULT
        )

    /** The catalogue rendered by the Free AI tab's model selector. */
    val freeModels: List<FreeAiModel> = FreeAiModel.ALL

    private val _webSearchMode = MutableStateFlow(false)
    val webSearchMode: StateFlow<Boolean> = _webSearchMode.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.selectedProviderId.collect { _selectedProviderId.value = it }
        }
        viewModelScope.launch {
            preferencesManager.selectedFreeModelId.collect { _selectedFreeModelId.value = it }
        }
    }

    /** Persists the Free AI tab model choice. */
    fun selectFreeModel(freeModelId: String) {
        viewModelScope.launch {
            preferencesManager.setSelectedFreeModelId(freeModelId)
        }
    }

    fun setFreeMode(free: Boolean) {
        _isFreeMode.value = free
    }

    fun setWebSearchMode(enabled: Boolean) {
        _webSearchMode.value = enabled
    }

    /**
     * Sends a user message. When [attachmentType] is provided the message stores the local
     * file reference and, for image attachments on a vision-capable premium provider, the
     * picture is forwarded so the model can actually see it. For unsupported attachments the
     * user is told clearly rather than failing silently.
     *
     * @param imageDataUri a base64 `data:image/…` payload (already read from the file by the
     * UI) used for vision requests; null when there is no image to send.
     */
    fun sendMessage(
        content: String,
        attachmentType: String? = null,
        attachmentUri: String? = null,
        attachmentName: String? = null,
        imageDataUri: String? = null
    ) {
        if (content.trim().isEmpty() && attachmentUri.isNullOrBlank()) return

        viewModelScope.launch {
            val isFree = _isFreeMode.value
            val freeModel = selectedFreeModel.value
            val provider = selectedProvider.value
            // Free replies are labelled "Free · <model>" so the Free tab can filter its own
            // history and the chat bubble can still show "Powered by <model>".
            val providerLabel = if (isFree) freeModel.chatLabel else provider.displayName

            val userMessage = ChatMessage(
                content = content.trim(),
                isUser = true,
                modelUsed = if (isFree) freeModel.chatLabel else null,
                attachmentType = attachmentType,
                attachmentUri = attachmentUri,
                attachmentName = attachmentName
            )
            chatMessageDao.insertMessage(userMessage)

            val visionProvider = !isFree && attachmentType == "image" &&
                imageDataUri != null && provider.supportsVision

            if (!visionProvider && !attachmentUri.isNullOrBlank()) {
                // The provider cannot use this attachment — keep it attached for the user's
                // own reference and clearly explain what did or didn't happen.
                val notice = when {
                    attachmentType == "image" && !isFree ->
                        "⚠️ ${provider.displayName} is a text-only chat model and can't view images. " +
                            "Switch to a vision-capable provider (Groq supports vision) to send photos."
                    attachmentType == "image" ->
                        "⚠️ The Free AI tab is text-only and can't view images. " +
                            "Use the 🖼 Image tool to generate a picture instead."
                    else ->
                        "📎 I've kept your ${attachmentType?.uppercase()} attachment (\"${attachmentName ?: "file"}\") " +
                            "with this message, but the selected provider can't read ${attachmentType ?: "file"} files. " +
                            "It wasn't sent for processing."
                }
                chatMessageDao.insertMessage(
                    ChatMessage(content = notice, isUser = false, modelUsed = providerLabel)
                )
                return@launch
            }

            _isGenerating.value = true
            try {
                val aiProvider: AiProvider = if (isFree) {
                    aiProviderFactory.getFreeProvider(freeModel.id)
                } else {
                    aiProviderFactory.getProvider(provider.id)
                }

                val history = chatMessageDao.getAllMessages()
                    .filter { FreeAiModel.isFreeLabel(it.modelUsed) == isFree }
                    .takeLast(20)

                val builder = StringBuilder()
                aiProvider.sendMessage(content, history, if (visionProvider) imageDataUri else null)
                    .collect { response ->
                        when (response) {
                            is AiResponse.Success -> builder.append(response.text)
                            is AiResponse.Error -> builder.clear().append(response.text)
                        }
                    }

                chatMessageDao.insertMessage(
                    ChatMessage(
                        content = builder.toString(),
                        isUser = false,
                        modelUsed = providerLabel
                    )
                )
            } catch (e: Exception) {
                val errorMessage = ChatMessage(
                    content = "Unable to fetch response. Please try again. (${e.localizedMessage ?: "Unknown Error"})",
                    isUser = false,
                    modelUsed = providerLabel
                )
                chatMessageDao.insertMessage(errorMessage)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    /**
     * Generates a real image via Pollinations' key-less image endpoint (Free AI tab only).
     * The resulting PNG is embedded as a base64 markdown image so the chat renders it inline.
     */
    fun generateImage(prompt: String) {
        if (prompt.trim().isEmpty()) return
        viewModelScope.launch {
            val freeModel = selectedFreeModel.value
            val providerLabel = freeModel.chatLabel
            chatMessageDao.insertMessage(
                ChatMessage(
                    content = "🖼 Generate image: ${prompt.trim()}",
                    isUser = true,
                    modelUsed = providerLabel
                )
            )

            _isGenerating.value = true
            try {
                val url = "https://image.pollinations.ai/prompt/" +
                    java.net.URLEncoder.encode(prompt.trim(), "UTF-8") +
                    "?width=512&height=512&nologo=true&model=flux&enhance=true"
                val request = Request.Builder().url(url).get().build()
                val bytes = okHttpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                    resp.body?.bytes() ?: throw IllegalStateException("Empty image body")
                }
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                val markdown = "![Generated image](data:image/png;base64,$base64)"
                chatMessageDao.insertMessage(
                    ChatMessage(content = markdown, isUser = false, modelUsed = providerLabel)
                )
            } catch (e: Exception) {
                chatMessageDao.insertMessage(
                    ChatMessage(
                        content = "🖼 I couldn't generate that image (${e.localizedMessage ?: "unknown error"}). " +
                            "Pollinations image generation may be rate-limited — please try again shortly.",
                        isUser = false,
                        modelUsed = providerLabel
                    )
                )
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun clearConversation() {
        viewModelScope.launch {
            chatMessageDao.clearAllMessages()
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onComplete()
        }
    }
}
