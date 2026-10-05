package com.salmanlaghari.pkai.data.repository

import com.salmanlaghari.pkai.data.local.room.ChatHistoryDao
import com.salmanlaghari.pkai.data.model.ChatHistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Hand-rolled fake so these tests never touch a real database. */
private class FakeChatHistoryDao : ChatHistoryDao {
    val items = mutableMapOf<String, ChatHistoryItem>()

    override suspend fun insertItem(item: ChatHistoryItem) {
        items[item.id] = item
    }

    override fun getAllHistoryFlow(): Flow<List<ChatHistoryItem>> = flowOf(items.values.toList())

    override suspend fun getAllHistory(): List<ChatHistoryItem> = items.values.toList()

    override suspend fun deleteItem(itemId: String) {
        items.remove(itemId)
    }

    override suspend fun renameItem(itemId: String, newTitle: String) {
        items[itemId]?.let { items[itemId] = it.copy(title = newTitle) }
    }

    override suspend fun setPinned(itemId: String, isPinned: Boolean) {
        items[itemId]?.let { items[itemId] = it.copy(isPinned = isPinned) }
    }

    override suspend fun isPinned(itemId: String): Boolean? {
        return items[itemId]?.isPinned
    }

    override suspend fun clearAll() {
        items.clear()
    }
}

class ChatHistoryRecorderTest {

    private lateinit var dao: FakeChatHistoryDao
    private lateinit var recorder: ChatHistoryRecorder

    @Before
    fun setUp() {
        dao = FakeChatHistoryDao()
        recorder = ChatHistoryRecorder(dao)
    }

    @Test
    fun `recordSession preserves an existing pin instead of silently unpinning`() = runTest {
        val id = "session-1"
        dao.insertItem(
            ChatHistoryItem(id = id, title = "old", lastMessage = "m", timestamp = 1L, isPinned = true)
        )

        val returned = recorder.recordSession(sessionId = id, title = "new", preview = "p2")

        assertEquals(id, returned)
        val stored = dao.items[id]!!
        assertTrue("recording must not unpin an entry pinned via togglePinItem", stored.isPinned)
        assertEquals("new", stored.title)
        assertEquals("p2", stored.lastMessage)
    }

    @Test
    fun `recordSession respects an explicit isPinned true on a fresh row`() = runTest {
        val id = recorder.recordSession(sessionId = "fresh", title = "t", preview = "p", isPinned = true)
        assertTrue(dao.items[id]?.isPinned == true)
    }

    @Test
    fun `recordSession generates an id when none is given`() = runTest {
        val id = recorder.recordSession(title = "t", preview = "p")
        assertTrue(id.isNotBlank())
        assertEquals("t", dao.items[id]?.title)
        assertFalse(dao.items[id]?.isPinned ?: true)
    }

    @Test
    fun `deleteSession removes the row`() = runTest {
        val id = recorder.recordSession(title = "t", preview = "p")
        recorder.deleteSession(id)
        assertTrue(dao.items.isEmpty())
    }

    @Test
    fun `clearHistory removes all rows`() = runTest {
        recorder.recordSession(title = "a", preview = "p")
        recorder.recordSession(title = "b", preview = "p")
        recorder.clearHistory()
        assertTrue(dao.items.isEmpty())
    }
}
