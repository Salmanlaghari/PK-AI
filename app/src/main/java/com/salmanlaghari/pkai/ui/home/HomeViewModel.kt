package com.salmanlaghari.pkai.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import com.salmanlaghari.pkai.data.local.room.ChatMessageDao
import com.salmanlaghari.pkai.data.model.AiModel
import com.salmanlaghari.pkai.data.model.ChatMessage
import com.salmanlaghari.pkai.data.remote.provider.AiProviderFactory
import com.salmanlaghari.pkai.data.repository.AppRepository
import com.salmanlaghari.pkai.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    private val _selectedModel = MutableStateFlow(AiModel.GEMINI)
    val selectedModel: StateFlow<AiModel> = _selectedModel.asStateFlow()

    private val _webSearchMode = MutableStateFlow(false)
    val webSearchMode: StateFlow<Boolean> = _webSearchMode.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    fun setFreeMode(free: Boolean) {
        _isFreeMode.value = free
    }

    fun setWebSearchMode(enabled: Boolean) {
        _webSearchMode.value = enabled
    }

    fun selectModel(model: AiModel) {
        _selectedModel.value = model
    }

    fun sendMessage(content: String) {
        if (content.trim().isEmpty()) return

        viewModelScope.launch {
            val isFree = _isFreeMode.value
            val webSearch = _webSearchMode.value
            val model = _selectedModel.value

            // 1. Insert user message (tag with "Free Public AI" if in free mode)
            val userMessage = ChatMessage(
                content = content.trim(),
                isUser = true,
                modelUsed = if (isFree) "Free Public AI" else "PK AI"
            )
            chatMessageDao.insertMessage(userMessage)

            // 2. Trigger AI generating response
            _isGenerating.value = true
            try {
                val provider = when {
                    isFree -> aiProviderFactory.getPublicFreeProvider()
                    webSearch -> aiProviderFactory.getProvider(AiModel.WEB)
                    else -> aiProviderFactory.getProvider(model)
                }
                val responseText = provider.generateResponse(content)
                val aiMessage = ChatMessage(
                    content = responseText,
                    isUser = false,
                    modelUsed = if (isFree) "Free Public AI" else "PK AI"
                )
                chatMessageDao.insertMessage(aiMessage)
            } catch (e: Exception) {
                val errorMessage = ChatMessage(
                    content = "Unable to fetch response. Please try again. (${e.localizedMessage ?: "Unknown Error"})",
                    isUser = false,
                    modelUsed = if (isFree) "Free Public AI" else "PK AI"
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
