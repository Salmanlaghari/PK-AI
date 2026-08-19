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
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val authRepository: AuthRepository,
    private val chatMessageDao: ChatMessageDao,
    private val aiProviderFactory: AiProviderFactory,
    private val preferencesManager: PreferencesManager
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

    fun sendMessage(content: String) {
        if (content.trim().isEmpty()) return

        viewModelScope.launch {
            val isFree = _isFreeMode.value
            val freeModel = selectedFreeModel.value
            // Free replies are labelled "Free · <model>" so the Free tab can filter its own
            // history and the chat bubble can still show "Powered by <model>".
            val providerLabel = if (isFree) freeModel.chatLabel else selectedProvider.value.displayName

            val userMessage = ChatMessage(
                content = content.trim(),
                isUser = true,
                modelUsed = if (isFree) freeModel.chatLabel else null
            )
            chatMessageDao.insertMessage(userMessage)

            _isGenerating.value = true
            try {
                val provider: AiProvider = if (isFree) {
                    aiProviderFactory.getFreeProvider(freeModel.id)
                } else {
                    aiProviderFactory.getProvider(selectedProvider.value.id)
                }

                val history = chatMessageDao.getAllMessages()
                    .filter { FreeAiModel.isFreeLabel(it.modelUsed) == isFree }
                    .takeLast(20)

                val builder = StringBuilder()
                provider.sendMessage(content, history).collect { response ->
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
