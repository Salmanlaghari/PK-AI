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
import kotlinx.coroutines.flow.flatMapLatest
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

    private val _selectedModel = MutableStateFlow(AiModel.GEMINI)
    val selectedModel: StateFlow<AiModel> = _selectedModel.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val chatMessages: StateFlow<List<ChatMessage>> = _selectedModel
        .flatMapLatest { model ->
            chatMessageDao.getMessagesForModelFlow(model.name)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    fun selectModel(model: AiModel) {
        _selectedModel.value = model
    }

    fun sendMessage(content: String) {
        if (content.trim().isEmpty()) return

        viewModelScope.launch {
            val model = _selectedModel.value
            // 1. Insert user message tagged with selected model name
            val userMessage = ChatMessage(
                content = content.trim(),
                isUser = true,
                modelUsed = model.name
            )
            chatMessageDao.insertMessage(userMessage)

            // 2. Trigger AI generating response
            _isGenerating.value = true
            try {
                val provider = aiProviderFactory.getProvider(model)
                val responseText = provider.generateResponse(content)
                val aiMessage = ChatMessage(
                    content = responseText,
                    isUser = false,
                    modelUsed = model.name
                )
                chatMessageDao.insertMessage(aiMessage)
            } catch (e: Exception) {
                val errorMessage = ChatMessage(
                    content = "Unable to fetch response. Please try again. (${e.localizedMessage ?: "Unknown Error"})",
                    isUser = false,
                    modelUsed = model.name
                )
                chatMessageDao.insertMessage(errorMessage)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun clearConversation() {
        viewModelScope.launch {
            chatMessageDao.clearMessagesForModel(_selectedModel.value.name)
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onComplete()
        }
    }
}
