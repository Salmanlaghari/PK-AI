package com.salmanlaghari.pkai.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import com.salmanlaghari.pkai.data.local.room.ChatMessageDao
import com.salmanlaghari.pkai.data.model.ChatMessage
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
            if (freeMode) {
                messages.filter { it.modelUsed == "Free Public AI" }
            } else {
                messages.filter { it.modelUsed != "Free Public AI" }
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

    private val _webSearchMode = MutableStateFlow(false)
    val webSearchMode: StateFlow<Boolean> = _webSearchMode.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.selectedProviderId.collect { _selectedProviderId.value = it }
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
            val providerLabel = if (isFree) "Free Public AI" else selectedProvider.value.displayName

            val userMessage = ChatMessage(
                content = content.trim(),
                isUser = true,
                modelUsed = if (isFree) "Free Public AI" else null
            )
            chatMessageDao.insertMessage(userMessage)

            _isGenerating.value = true
            try {
                val provider: AiProvider = if (isFree) {
                    aiProviderFactory.getPublicFreeProvider()
                } else {
                    aiProviderFactory.getProvider(selectedProvider.value.id)
                }

                val history = chatMessageDao.getAllMessages()
                    .filter { it.modelUsed != "Free Public AI" }
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
