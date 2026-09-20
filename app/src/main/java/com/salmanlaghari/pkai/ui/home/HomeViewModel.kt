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
import com.salmanlaghari.pkai.data.repository.CodeExecutionResult
import com.salmanlaghari.pkai.data.repository.CodeRunnerRepository
import com.salmanlaghari.pkai.data.repository.ImageGenerationResult
import com.salmanlaghari.pkai.data.repository.PollinationsImageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import java.io.File
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Base64

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
    private val authRepository: AuthRepository,
    private val chatMessageDao: ChatMessageDao,
    private val aiProviderFactory: AiProviderFactory,
    private val preferencesManager: PreferencesManager,
    private val codeRunnerRepository: CodeRunnerRepository,
    private val pollinationsImageRepository: PollinationsImageRepository
) : ViewModel() {

    /** Hugging Face text-to-image model used by the dedicated Image Generation tab. */
    private companion object {
        const val IMAGE_PROVIDER_LABEL = "PK AI Image"
        const val TAG = "HomeViewModel"
    }

    private val _isFreeMode = MutableStateFlow(false)
    val isFreeMode: StateFlow<Boolean> = _isFreeMode.asStateFlow()

    private val _isImageMode = MutableStateFlow(false)
    val isImageMode: StateFlow<Boolean> = _isImageMode.asStateFlow()

    val chatMessages: StateFlow<List<ChatMessage>> = chatMessageDao.getAllMessagesFlow()
        .catch { e ->
            Log.e(TAG, "Failed reading chat messages from database", e)
            emit(emptyList())
        }
        .combine(_isImageMode) { messages, imageMode ->
            imageMode to messages
        }
        .combine(_isFreeMode) { (imageMode, messages), freeMode ->
            when {
                imageMode -> messages.filter {
                    it.modelUsed == IMAGE_PROVIDER_LABEL || it.content.startsWith("🖼 Generate image:")
                }
                freeMode -> messages.filter {
                    FreeAiModel.isFreeLabel(it.modelUsed) && it.modelUsed != IMAGE_PROVIDER_LABEL && !it.content.startsWith("🖼 Generate image:")
                }
                else -> messages.filter {
                    !FreeAiModel.isFreeLabel(it.modelUsed) && it.modelUsed != IMAGE_PROVIDER_LABEL && !it.content.startsWith("🖼 Generate image:")
                }
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

    /**
     * The provider the UI should *display* as active. Normally this is the user's selected
     * provider, but after a rate-limit fallback it is temporarily overridden to the provider
     * that actually answered, so the chip / "Powered by" tag reflect reality.
     */
    private val _activeProviderOverride = MutableStateFlow<LlmProvider?>(null)
    val effectiveProvider: StateFlow<LlmProvider> = combine(selectedProvider, _activeProviderOverride) { selected, override ->
        override ?: selected
    }.stateIn(
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

    /** Text shown in the typing indicator — switches to an image-specific message. */
    private val _generatingLabel = MutableStateFlow("AI is thinking…")
    val generatingLabel: StateFlow<String> = _generatingLabel.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.selectedProviderId.collect { id ->
                _selectedProviderId.value = id
                // A fresh selection clears any transient fallback override.
                _activeProviderOverride.value = null
            }
        }
        viewModelScope.launch {
            preferencesManager.selectedFreeModelId.collect { _selectedFreeModelId.value = it }
        }
        sanitizeOldBase64Messages()
    }

    private fun sanitizeOldBase64Messages() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val all = chatMessageDao.getAllMessages()
                val imagesDir = File(context.filesDir, "generated_images").apply { mkdirs() }
                for (msg in all) {
                    if (msg.content.contains("data:image/") && msg.content.contains(";base64,")) {
                        val startIndex = msg.content.indexOf(";base64,") + 8
                        val endIndex = msg.content.indexOf(")", startIndex).takeIf { it > 0 } ?: msg.content.length
                        val b64 = msg.content.substring(startIndex, endIndex).trim()
                        val bytes = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull()
                        if (bytes != null && bytes.isNotEmpty()) {
                            val file = File(imagesDir, "migrated_${msg.id.take(8)}.jpg")
                            file.writeBytes(bytes)
                            val newContent = "![Generated image](file://${file.absolutePath})"
                            chatMessageDao.insertMessage(msg.copy(content = newContent))
                        } else {
                            chatMessageDao.insertMessage(msg.copy(content = "🖼 [Generated image]"))
                        }
                    }
                }
            }
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
        if (free) {
            _isImageMode.value = false
            _activeProviderOverride.value = null
        }
    }

    fun setImageMode(image: Boolean) {
        _isImageMode.value = image
        if (image) {
            _isFreeMode.value = false
            _activeProviderOverride.value = null
        }
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
     * In Image mode the text is treated as a text-to-image prompt and routed to the Hugging
     * Face image model instead of a chat provider.
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

        // Image Generation tab handles the text as a picture prompt.
        if (_isImageMode.value) {
            generateHuggingFaceImage(content.trim())
            return
        }

        viewModelScope.launch {
            val isFree = _isFreeMode.value
            val freeModel = selectedFreeModel.value
            val provider = selectedProvider.value
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

            // Text-only chat providers cannot generate images — decline such requests
            // honestly instead of fabricating fake image markdown.
            if (!isFree && attachmentType != "image" && looksLikeImageRequest(content)) {
                chatMessageDao.insertMessage(
                    ChatMessage(
                        content = "🎨 ${provider.displayName} is a text-only chat model and can't generate images. " +
                            "Switch to the 🖼 Image tab to create pictures with Hugging Face.",
                        isUser = false,
                        modelUsed = providerLabel
                    )
                )
                return@launch
            }

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
                val history = chatMessageDao.getAllMessages()
                    .filter { FreeAiModel.isFreeLabel(it.modelUsed) == isFree }
                    .takeLast(20)

                val builder = StringBuilder()
                var answeredBy: LlmProvider? = null
                var answeredFreeModel: FreeAiModel? = null
                var firstFailureReason: String? = null
                var lastError: String? = null

                if (isFree) {
                    // Free path: try the selected key-less model first, then fall through the
                    // remaining free models so a busy / per-IP-limited endpoint (e.g. Ox Alpha's
                    // anonymous Turnstile checkpoint) never dead-ends the chat with a raw error.
                    val freeChain = listOf(freeModel) + FreeAiModel.ALL.filter { it.id != freeModel.id }
                    for (candidate in freeChain) {
                        val instance = aiProviderFactory.getFreeProvider(candidate.id)
                        var text: String? = null
                        var err: String? = null
                        instance.sendMessage(content, history, if (visionProvider) imageDataUri else null)
                            .collect { response ->
                                when (response) {
                                    is AiResponse.Success -> text = response.text
                                    is AiResponse.Error -> err = response.text
                                }
                            }

                        if (!text.isNullOrBlank()) {
                            builder.append(text)
                            answeredFreeModel = candidate
                            break
                        }

                        if (firstFailureReason == null) firstFailureReason = err
                        lastError = err
                    }
                    if (answeredFreeModel == null) builder.clear().append(lastError ?: "Unknown error")
                } else {
                    // Premium path: try the selected provider, then fall back through the
                    // ordered chain when it is rate-limited / out of quota / temporarily unavailable.
                    val chain = aiProviderFactory.fallbackChain(provider.id).ifEmpty { listOf(provider) }
                    var failureKind: ProviderErrorKind = ProviderErrorKind.Unknown
                    for (candidate in chain) {
                        val instance = aiProviderFactory.getProvider(candidate.id)
                        var text: String? = null
                        var err: String? = null
                        instance.sendMessage(content, history, if (visionProvider) imageDataUri else null)
                            .collect { response ->
                                when (response) {
                                    is AiResponse.Success -> text = response.text
                                    is AiResponse.Error -> err = response.text
                                }
                            }

                        if (!text.isNullOrBlank()) {
                            builder.append(text)
                            answeredBy = candidate
                            break
                        }

                        if (firstFailureReason == null) firstFailureReason = err
                        lastError = err
                        failureKind = classifyError(err)

                        // Only transient/provider-affecting errors justify burning another provider.
                        if (failureKind == ProviderErrorKind.RateLimit ||
                            failureKind == ProviderErrorKind.QuotaExceeded ||
                            failureKind == ProviderErrorKind.AuthFailure ||
                            failureKind == ProviderErrorKind.ServerUnavailable ||
                            failureKind == ProviderErrorKind.Network
                        ) continue
                        builder.clear().append(err ?: "Unknown error")
                        break
                    }
                }

                val finalLabel = if (isFree) (answeredFreeModel ?: freeModel).chatLabel else (answeredBy ?: provider).displayName

                // Free tier: when a fallback model answered, tell the user which one did.
                if (isFree && answeredFreeModel != null && answeredFreeModel.id != freeModel.id) {
                    chatMessageDao.insertMessage(
                        ChatMessage(
                            content = "↪ Switched to ${answeredFreeModel.displayName} (${freeModel.displayName} unavailable)",
                            isUser = false,
                            modelUsed = null
                        )
                    )
                }

                // After a successful fallback, surface which provider actually answered and
                // nudge the active-provider indicator to match.
                if (!isFree && answeredBy != null && answeredBy.id != provider.id) {
                    val kind = classifyError(firstFailureReason)
                    val reason = when (kind) {
                        ProviderErrorKind.RateLimit -> "${provider.displayName} temporarily rate-limited"
                        ProviderErrorKind.QuotaExceeded -> "${provider.displayName} quota exhausted"
                        ProviderErrorKind.AuthFailure -> "${provider.displayName} authentication failed"
                        ProviderErrorKind.ServerUnavailable -> "${provider.displayName} servers unavailable"
                        ProviderErrorKind.Network -> "${provider.displayName} network error"
                        else -> "${provider.displayName} unavailable"
                    }
                    chatMessageDao.insertMessage(
                        ChatMessage(
                            content = "↪ Switched to ${answeredBy.displayName} ($reason)",
                            isUser = false,
                            modelUsed = null
                        )
                    )
                    _activeProviderOverride.value = answeredBy
                }

                if (builder.isBlank()) {
                    // Reached only when every candidate errored — show one clear message.
                    chatMessageDao.insertMessage(
                        ChatMessage(
                            content = lastError ?: "All providers failed to respond. Please try again.",
                            isUser = false,
                            modelUsed = finalLabel
                        )
                    )
                } else {
                    chatMessageDao.insertMessage(
                        ChatMessage(
                            content = builder.toString(),
                            isUser = false,
                            modelUsed = finalLabel
                        )
                    )
                }
            } catch (e: Exception) {
                val errorMessage = ChatMessage(
                    content = "Unable to fetch response. Please try again. (${e.localizedMessage ?: "Unknown Error"})",
                    isUser = false,
                    modelUsed = if (isFree) freeModel.chatLabel else provider.displayName
                )
                chatMessageDao.insertMessage(errorMessage)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    /**
     * Generates a real image via Pollinations' key-less image endpoint (Flux), rendered
     * inline as a Bitmap in the chat. No API key needed — works out of the box.
     *
     * Pollinations occasionally rate-limits bursts; we retry a few times with backoff and
     * keep the UI in a "Generating image, please wait…" state until it succeeds or gives up.
     */
    fun generateHuggingFaceImage(prompt: String) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            chatMessageDao.insertMessage(
                ChatMessage(
                    content = "🖼 Generate image: $prompt",
                    isUser = true,
                    modelUsed = IMAGE_PROVIDER_LABEL
                )
            )

            _isGenerating.value = true
            _generatingLabel.value = "Generating image, please wait…"
            try {
                val markdown = withContext(Dispatchers.IO) {
                    when (val result = pollinationsImageRepository.generateImage(prompt)) {
                        is ImageGenerationResult.Success -> {
                            val imagesDir = File(context.filesDir, "generated_images").apply { mkdirs() }
                            val imageFile = File(imagesDir, "img_${System.currentTimeMillis()}.jpg")
                            imageFile.writeBytes(result.bytes)
                            "![Generated image](file://${imageFile.absolutePath})"
                        }
                        is ImageGenerationResult.RateLimited -> "🖼 Image generation rate-limited. ${result.message} Please try again shortly."
                        is ImageGenerationResult.QuotaExceeded -> "🖼 Image generation quota exceeded. ${result.message}"
                        is ImageGenerationResult.AuthenticationFailed -> "🖼 Image generation authentication failed. ${result.message}"
                        is ImageGenerationResult.Unavailable -> "🖼 Image generation unavailable. ${result.message}"
                        is ImageGenerationResult.InvalidRequest -> "🖼 Invalid image request. ${result.message}"
                        is ImageGenerationResult.NetworkError -> "🖼 Network error generating image. ${result.message}"
                    }
                }
                chatMessageDao.insertMessage(
                    ChatMessage(content = markdown, isUser = false, modelUsed = IMAGE_PROVIDER_LABEL)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error generating image", e)
                chatMessageDao.insertMessage(
                    ChatMessage(
                        content = "🖼 I couldn't generate that image (${e.localizedMessage ?: "unknown error"}). " +
                            "The image service may be rate-limited — please try again shortly.",
                        isUser = false,
                        modelUsed = IMAGE_PROVIDER_LABEL
                    )
                )
            } finally {
                _isGenerating.value = false
                _generatingLabel.value = "AI is thinking…"
            }
        }
    }

    /**
     * Generates a real image via Pollinations' key-less image endpoint (Free AI tab only).
     * The resulting PNG is stored in internal storage so the chat renders it inline safely.
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
                val markdown = withContext(Dispatchers.IO) {
                    when (val result = pollinationsImageRepository.generateImage(prompt.trim())) {
                        is ImageGenerationResult.Success -> {
                            val imagesDir = File(context.filesDir, "generated_images").apply { mkdirs() }
                            val imageFile = File(imagesDir, "img_${System.currentTimeMillis()}.jpg")
                            imageFile.writeBytes(result.bytes)
                            "![Generated image](file://${imageFile.absolutePath})"
                        }
                        is ImageGenerationResult.RateLimited -> "🖼 Image generation rate-limited. ${result.message} Please try again shortly."
                        is ImageGenerationResult.QuotaExceeded -> "🖼 Image generation quota exceeded. ${result.message}"
                        is ImageGenerationResult.AuthenticationFailed -> "🖼 Image generation authentication failed. ${result.message}"
                        is ImageGenerationResult.Unavailable -> "🖼 Image generation unavailable. ${result.message}"
                        is ImageGenerationResult.InvalidRequest -> "🖼 Invalid image request. ${result.message}"
                        is ImageGenerationResult.NetworkError -> "🖼 Network error generating image. ${result.message}"
                    }
                }
                chatMessageDao.insertMessage(
                    ChatMessage(content = markdown, isUser = false, modelUsed = providerLabel)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error generating free image", e)
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

    private fun isRateLimitOrQuota(error: String?): Boolean {
        if (error == null) return false
        val e = error.lowercase()
        return e.contains("429") || e.contains("rate limit") || e.contains("quota") || e.contains("402")
    }

    private sealed interface ProviderErrorKind {
        object RateLimit : ProviderErrorKind
        object QuotaExceeded : ProviderErrorKind
        object AuthFailure : ProviderErrorKind
        object ModelNotFound : ProviderErrorKind
        object InvalidRequest : ProviderErrorKind
        object ServerUnavailable : ProviderErrorKind
        object Network : ProviderErrorKind
        object Unknown : ProviderErrorKind
    }

    private fun classifyError(errorText: String?): ProviderErrorKind {
        if (errorText.isNullOrBlank()) return ProviderErrorKind.Unknown
        val lower = errorText.lowercase()
        return when {
            lower.contains("429") || lower.contains("rate limit") -> ProviderErrorKind.RateLimit
            lower.contains("402") || lower.contains("quota") -> ProviderErrorKind.QuotaExceeded
            lower.contains("401") || lower.contains("403") || lower.contains("authentication failed") || lower.contains("auth failure") -> ProviderErrorKind.AuthFailure
            lower.contains("404") || lower.contains("model_not_found") || lower.contains("model not found") -> ProviderErrorKind.ModelNotFound
            lower.contains("400") || lower.contains("invalid request") -> ProviderErrorKind.InvalidRequest
            lower.contains("500") || lower.contains("502") || lower.contains("503") || lower.contains("504") || lower.contains("unavailable") -> ProviderErrorKind.ServerUnavailable
            lower.contains("timeout") || lower.contains("network") || lower.contains("unknown host") || lower.contains("i/o error") -> ProviderErrorKind.Network
            else -> ProviderErrorKind.Unknown
        }
    }

    /** Conservative heuristic: does the user seem to be asking for an image to be drawn? */
    private fun looksLikeImageRequest(prompt: String): Boolean {
        val p = prompt.lowercase()
        return p.contains("generate an image") || p.contains("generate image") ||
            p.contains("generate a picture") || p.contains("create an image") ||
            p.contains("create a picture") || p.contains("make an image") ||
            p.contains("text to image") || p.contains("text-to-image") ||
            p.contains("image of") || p.contains("picture of") ||
            p.startsWith("image:") || p.startsWith("draw ") || p.startsWith("draw me") ||
            p.contains("paint a") || p.contains("render a")
    }

    /** Executes a code snippet using HackerEarth API / Proxy. */
    fun runCode(source: String, lang: String, onResult: (CodeExecutionResult) -> Unit) {
        viewModelScope.launch {
            val result = codeRunnerRepository.executeCode(source, lang)
            onResult(result)
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
