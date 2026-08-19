package com.salmanlaghari.pkai.ui.home

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever
import org.mockito.ArgumentMatchers.anyString

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
    private lateinit var selectedProviderFlow: MutableStateFlow<String>

    private lateinit var viewModel: HomeViewModel
    private lateinit var collectJob: Job

    private val messagesList = mutableListOf<ChatMessage>()
    private val messagesFlow = MutableStateFlow<List<ChatMessage>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakeAuthRepository = mock(AuthRepository::class.java)
        fakeAppRepository = mock(AppRepository::class.java)
        mockPreferencesManager = mock(PreferencesManager::class.java)
        mockAiProviderFactory = mock(AiProviderFactory::class.java)

        // Fake DAO implementation
        fakeChatMessageDao = object : ChatMessageDao {
            override suspend fun insertMessage(message: ChatMessage) {
                messagesList.add(message)
                messagesFlow.value = messagesList.toList()
            }

            override fun getAllMessagesFlow(): Flow<List<ChatMessage>> = messagesFlow

            override suspend fun getAllMessages(): List<ChatMessage> {
                return messagesList.toList()
            }

            override suspend fun clearAllMessages() {
                messagesList.clear()
                messagesFlow.value = emptyList()
            }
        }

        // Selected provider defaults to Groq
        selectedProviderFlow = MutableStateFlow(LlmProvider.DEFAULT.id)
        whenever(mockPreferencesManager.selectedProviderId).thenReturn(selectedProviderFlow)
        whenever(mockPreferencesManager.selectedFreeModelId).thenReturn(flowOf(FreeAiModel.DEFAULT.id))

        // Premium provider returns a predictable response via the new Flow API
        val mockAiProvider = object : AiProvider {
            override fun sendMessage(
                prompt: String,
                history: List<ChatMessage>,
                imageDataUri: String?
            ): Flow<AiResponse> = flow {
                emit(AiResponse.Success("Response for prompt: $prompt"))
            }
        }
        whenever(mockAiProviderFactory.getProvider(anyString())).thenReturn(mockAiProvider)

        // Free provider
        val mockFreeAiProvider = object : AiProvider {
            override fun sendMessage(
                prompt: String,
                history: List<ChatMessage>,
                imageDataUri: String?
            ): Flow<AiResponse> = flow {
                emit(AiResponse.Success("Free response for prompt: $prompt"))
            }
        }
        whenever(mockAiProviderFactory.getFreeProvider(anyString())).thenReturn(mockFreeAiProvider)

        viewModel = HomeViewModel(
            appRepository = fakeAppRepository,
            authRepository = fakeAuthRepository,
            chatMessageDao = fakeChatMessageDao,
            aiProviderFactory = mockAiProviderFactory,
            preferencesManager = mockPreferencesManager,
            okHttpClient = mock(OkHttpClient::class.java)
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
        assertEquals(LlmProvider.DEFAULT.id, viewModel.selectedProvider.value.id)
        assertEquals(false, viewModel.isGenerating.value)
        assertTrue(viewModel.chatMessages.value.isEmpty())
    }

    @Test
    fun `sendMessage inserts prompt and generates AI response successfully`() {
        // Given
        val prompt = "Hello PK AI"
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

        // Second is AI response labelled with the active provider
        val secondMsg = currentMessages[1]
        assertEquals("Response for prompt: Hello PK AI", secondMsg.content)
        assertEquals(false, secondMsg.isUser)
        assertEquals("Groq", secondMsg.modelUsed)
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

    @Test
    fun `switching to freeMode partitions chat history and uses publicFreeProvider`() {
        // Given
        viewModel.sendMessage("Premium Query")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, viewModel.chatMessages.value.size)

        // When
        viewModel.setFreeMode(true)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: History is empty for free mode initially
        assertTrue(viewModel.chatMessages.value.isEmpty())

        // Send a free message
        viewModel.sendMessage("Free Query")
        testDispatcher.scheduler.advanceUntilIdle()

        val freeMessages = viewModel.chatMessages.value
        assertEquals(2, freeMessages.size)
        assertEquals("Free Query", freeMessages[0].content)
        assertEquals("Free response for prompt: Free Query", freeMessages[1].content)
        assertEquals(FreeAiModel.DEFAULT.chatLabel, freeMessages[1].modelUsed)

        // Switch back to premium
        viewModel.setFreeMode(false)
        testDispatcher.scheduler.advanceUntilIdle()

        val premiumMessages = viewModel.chatMessages.value
        assertEquals(2, premiumMessages.size)
        assertEquals("Premium Query", premiumMessages[0].content)
    }

    @Test
    fun `selecting a different provider in Settings propagates to the ViewModel`() {
        // The UI subscribes to effectiveProvider; mirror that so the StateFlow is active.
        val sub = CoroutineScope(testDispatcher).launch { viewModel.effectiveProvider.collect {} }

        // Given the default is Groq
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("groq", viewModel.selectedProvider.value.id)
        assertEquals("groq", viewModel.effectiveProvider.value.id)

        // When the user picks Mistral in Settings
        selectedProviderFlow.value = "mistral"
        testDispatcher.scheduler.advanceUntilIdle()

        // Then the active provider (and UI-facing effectiveProvider) updates
        assertEquals("mistral", viewModel.selectedProvider.value.id)
        assertEquals("Mistral AI", viewModel.effectiveProvider.value.displayName)
        assertEquals("mistral", viewModel.effectiveProvider.value.id)

        sub.cancel()
    }

    @Test
    fun `rate-limited provider falls back to the next provider and notes the switch`() {
        // Given the selected provider (Groq) returns HTTP 429 and Cerebras succeeds.
        val groqRateLimited = object : AiProvider {
            override fun sendMessage(
                prompt: String,
                history: List<ChatMessage>,
                imageDataUri: String?
            ): Flow<AiResponse> = flow {
                emit(AiResponse.Error("Groq: Rate limit exceeded (HTTP 429). Please wait and try again."))
            }
        }
        val cerebrasOk = object : AiProvider {
            override fun sendMessage(
                prompt: String,
                history: List<ChatMessage>,
                imageDataUri: String?
            ): Flow<AiResponse> = flow {
                emit(AiResponse.Success("Cerebras handled it"))
            }
        }
        whenever(mockAiProviderFactory.fallbackChain(anyString()))
            .thenReturn(listOf(LlmProvider.fromId("groq"), LlmProvider.fromId("cerebras")))
        whenever(mockAiProviderFactory.getProvider("groq")).thenReturn(groqRateLimited)
        whenever(mockAiProviderFactory.getProvider("cerebras")).thenReturn(cerebrasOk)

        // When
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.sendMessage("Hello")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then the answer comes from Cerebras and a switch note is shown
        val messages = viewModel.chatMessages.value
        val aiMessage = messages.last()
        assertEquals("Cerebras handled it", aiMessage.content)
        assertEquals("Cerebras", aiMessage.modelUsed)

        val switchNote = messages.find { it.content.startsWith("↪ Switched to") }
        assertTrue(switchNote != null)
        assertTrue(switchNote!!.content.contains("Cerebras"))
        assertTrue(switchNote.content.contains("Groq limit reached"))
    }
}
