package com.salmanlaghari.pkai.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salmanlaghari.pkai.data.local.room.ChatHistoryDao
import com.salmanlaghari.pkai.data.model.ChatHistoryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val chatHistoryDao: ChatHistoryDao
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val groupedHistory: StateFlow<List<HistoryUiItem>> = combine(
        chatHistoryDao.getAllHistoryFlow(),
        _searchQuery
    ) { rawHistory, query ->
        var history = rawHistory
        if (history.isEmpty()) {
            prepopulateFakeHistory()
            history = chatHistoryDao.getAllHistory()
        }

        // Apply Search Filtering
        val filtered = if (query.isBlank()) {
            history
        } else {
            history.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.lastMessage.contains(query, ignoreCase = true)
            }
        }

        groupHistoryItems(filtered)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun renameItem(itemId: String, newTitle: String) {
        viewModelScope.launch {
            chatHistoryDao.renameItem(itemId, newTitle)
        }
    }

    fun togglePinItem(chat: ChatHistoryItem) {
        viewModelScope.launch {
            chatHistoryDao.setPinned(chat.id, !chat.isPinned)
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            chatHistoryDao.deleteItem(itemId)
        }
    }

    private fun prepopulateFakeHistory() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val oneDay = 86400000L

            val items = listOf(
                ChatHistoryItem(
                    title = "💎 Gemini: Marketing Copy",
                    lastMessage = "Here is your customized marketing strategy copy for PK AI...",
                    timestamp = now,
                    isPinned = true
                ),
                ChatHistoryItem(
                    title = "🤖 ChatGPT: Code Architecting",
                    lastMessage = "I recommend using Hilt with standard Navigation Component...",
                    timestamp = now - 500000,
                    isPinned = false
                ),
                ChatHistoryItem(
                    title = "🧠 Claude: Legal Alignment",
                    lastMessage = "To establish a safe user agreement, we must state that...",
                    timestamp = now - oneDay + 10000,
                    isPinned = false
                ),
                ChatHistoryItem(
                    title = "⚡ Grok: Global Market Factual Analysis",
                    lastMessage = "Real-time signals reflect deep tech bullish trends across...",
                    timestamp = now - oneDay * 3,
                    isPinned = false
                ),
                ChatHistoryItem(
                    title = "🌊 DeepSeek: Logic & Math Calculations",
                    lastMessage = "The optimized polynomial equation reduces processing overhead by...",
                    timestamp = now - oneDay * 12,
                    isPinned = false
                )
            )

            items.forEach { chatHistoryDao.insertItem(it) }
        }
    }

    private fun groupHistoryItems(items: List<ChatHistoryItem>): List<HistoryUiItem> {
        if (items.isEmpty()) return emptyList()

        val today = mutableListOf<ChatHistoryItem>()
        val yesterday = mutableListOf<ChatHistoryItem>()
        val lastSevenDays = mutableListOf<ChatHistoryItem>()
        val older = mutableListOf<ChatHistoryItem>()

        val now = System.currentTimeMillis()
        val oneDay = 86400000L

        items.forEach { item ->
            val diff = now - item.timestamp
            when {
                diff < oneDay -> today.add(item)
                diff < 2 * oneDay -> yesterday.add(item)
                diff < 7 * oneDay -> lastSevenDays.add(item)
                else -> older.add(item)
            }
        }

        val resultList = mutableListOf<HistoryUiItem>()

        if (today.isNotEmpty()) {
            resultList.add(HistoryUiItem.Header("Today"))
            resultList.addAll(today.map { HistoryUiItem.Card(it) })
        }
        if (yesterday.isNotEmpty()) {
            resultList.add(HistoryUiItem.Header("Yesterday"))
            resultList.addAll(yesterday.map { HistoryUiItem.Card(it) })
        }
        if (lastSevenDays.isNotEmpty()) {
            resultList.add(HistoryUiItem.Header("Last 7 Days"))
            resultList.addAll(lastSevenDays.map { HistoryUiItem.Card(it) })
        }
        if (older.isNotEmpty()) {
            resultList.add(HistoryUiItem.Header("Older Conversations"))
            resultList.addAll(older.map { HistoryUiItem.Card(it) })
        }

        return resultList
    }
}
