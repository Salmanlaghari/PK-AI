package com.salmanlaghari.pkai.ui.home

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import com.salmanlaghari.pkai.data.local.room.ChatMessageDao
import com.salmanlaghari.pkai.data.model.AiModel
import com.salmanlaghari.pkai.data.model.ChatMessage
import com.salmanlaghari.pkai.data.remote.provider.AiProvider
import com.salmanlaghari.pkai.data.remote.provider.AiProviderFactory
import com.salmanlaghari.pkai.data.repository.AppRepository
import com.salmanlaghari.pkai.data.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeAuthRepository: AuthRepository
    private lateinit var fakeAppRepository: AppRepository
    private lateinit var fakeChatMessageDao: ChatMessageDao
    private lateinit var mockAiProviderFactory: AiProviderFactory
    private lateinit var mockPreferencesManager: PreferencesManager

    private lateinit var viewModel: HomeViewModel
    private lateinit var collectJob: Job

    private val messagesList = mutableListOf<ChatMessage>()
    private val messagesFlow = MutableStateFlow<List<ChatMessage>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        messagesList.clear()
        messagesFlow.value = emptyList()

        fakeAuthRepository = mock(AuthRepository::class.java)
        fakeAppRepository = mock(AppRepository::class.java)
        mockPreferencesManager = mock(PreferencesManager::class.java)
        mockAiProviderFactory = mock(AiProviderFactory::class.java)

        // Fake DAO implementation
        fakeChatMessageDao = object : ChatMessageDao {
            override suspend fun insertMessage(message: ChatMessage) {
                val index = messagesList.indexOfFirst { it.id == message.id }
                if (index != -1) {
                    messagesList[index] = message
                } else {
                    messagesList.add(message)
                }
                messagesFlow.value = messagesList.toList()
            }

            override fun getAllMessagesFlow(): Flow<List<ChatMessage>> = messagesFlow

            override suspend fun getAllMessages(): List<ChatMessage> {
                return messagesList.toList()
            }

            override suspend fun getMessageById(id: String): ChatMessage? {
                return messagesList.find { it.id == id }
            }

            override suspend fun clearAllMessages() {
                messagesList.clear()
                messagesFlow.value = emptyList()
            }
        }

        // Mock AiProviderFactory to return a custom AiProvider based on input
        val mockAiProvider = object : AiProvider {
            override fun generateResponseStream(prompt: String): Flow<String> {
                return kotlinx.coroutines.flow.flow {
                    val currentModel = viewModel.selectedModel.value
                    emit("Response from ${currentModel.displayName} for prompt: $prompt")
                }
            }
        }

        // Bypassing NullPointerException by stubbing for all Enum values explicitly
        for (model in AiModel.values()) {
            whenever(mockAiProviderFactory.getProvider(model)).thenReturn(mockAiProvider)
        }

        viewModel = HomeViewModel(
            appRepository = fakeAppRepository,
            authRepository = fakeAuthRepository,
            chatMessageDao = fakeChatMessageDao,
            aiProviderFactory = mockAiProviderFactory,
            preferencesManager = mockPreferencesManager
        )

        // Start collecting chatMessages Flow to activate WhileSubscribed collection
        collectJob = CoroutineScope(testDispatcher).launch {
            viewModel.chatMessages.collect {}
        }
    }

    @After
    fun tearDown() {
        collectJob.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `initial states are correctly setup`() {
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(AiModel.GEMINI, viewModel.selectedModel.value)
        assertEquals(false, viewModel.isGenerating.value)
        assertTrue(viewModel.chatMessages.value.isEmpty())
    }

    @Test
    fun `selectModel updates selected model state`() {
        viewModel.selectModel(AiModel.CHATGPT)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(AiModel.CHATGPT, viewModel.selectedModel.value)
    }

    @Test
    fun `sendMessage inserts prompt and generates AI response successfully`() {
        // Given
        val prompt = "Hello PK AI"
        viewModel.selectModel(AiModel.GEMINI)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.sendMessage(prompt)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val currentMessages = viewModel.chatMessages.value
        assertEquals(2, currentMessages.size)

        // First is user prompt
        val firstMsg = currentMessages[0]
        assertEquals(prompt, firstMsg.content)
        assertTrue(firstMsg.isUser)

        // Second is AI response
        val secondMsg = currentMessages[1]
        assertEquals("Response from Gemini for prompt: Hello PK AI", secondMsg.content)
        assertEquals(false, secondMsg.isUser)
        assertEquals("Gemini", secondMsg.modelUsed)
    }

    @Test
    fun `clearConversation clears the chat history successfully`() {
        // Given
        viewModel.sendMessage("Test Message")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, viewModel.chatMessages.value.size)

        // When
        viewModel.clearConversation()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertTrue(viewModel.chatMessages.value.isEmpty())
    }
}
