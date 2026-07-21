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

    val chatMessages: StateFlow<List<ChatMessage>> = chatMessageDao.getAllMessagesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedModel = MutableStateFlow(AiModel.GEMINI)
    val selectedModel: StateFlow<AiModel> = _selectedModel.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    fun selectModel(model: AiModel) {
        _selectedModel.value = model
    }

    fun sendMessage(content: String) {
        if (content.trim().isEmpty()) return

        viewModelScope.launch {
            val model = _selectedModel.value
            // 1. Insert user message
            val userMessage = ChatMessage(
                content = content.trim(),
                isUser = true,
                modelUsed = null
            )
            chatMessageDao.insertMessage(userMessage)

            // 2. Trigger AI generating response
            _isGenerating.value = true

            // Insert empty/placeholder AI message to start streaming into it
            val aiMessageId = java.util.UUID.randomUUID().toString()
            val aiMessage = ChatMessage(
                id = aiMessageId,
                content = "Thinking...",
                isUser = false,
                modelUsed = model.displayName
            )
            chatMessageDao.insertMessage(aiMessage)

            try {
                val provider = aiProviderFactory.getProvider(model)
                provider.generateResponseStream(content).collect { accumulatedText ->
                    if (accumulatedText.isNotBlank()) {
                        chatMessageDao.insertMessage(
                            aiMessage.copy(content = accumulatedText)
                        )
                    }
                }
            } catch (e: Exception) {
                val currentMsg = chatMessageDao.getMessageById(aiMessageId)
                val currentText = currentMsg?.content?.takeIf { it != "Thinking..." && it.isNotBlank() }
                val errorSuffix = "\n\n[Error: ${e.localizedMessage ?: "Unknown network error"}]"
                val finalText = if (currentText != null) {
                    currentText + errorSuffix
                } else {
                    "Unable to fetch response. Please try again. (${e.localizedMessage ?: "Unknown Error"})"
                }
                chatMessageDao.insertMessage(
                    aiMessage.copy(content = finalText)
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
