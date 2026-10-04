package com.salmanlaghari.pkai.data.repository

import com.salmanlaghari.pkai.data.local.room.ChatHistoryDao
import com.salmanlaghari.pkai.data.model.ChatHistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single write path for session-level chat history.
 *
 * [ChatHistoryDao] is provided in DI but nothing ever wrote to it, so the
 * History screen stayed empty. Call [recordSession] from a ViewModel whenever
 * a chat session starts or receives its first AI reply; every method is
 * main-safe (IO dispatcher internally) so it can be called from any coroutine.
 *
 * Recording is keyed by [sessionId]: calling it again for the same session
 * replaces the row (Room REPLACE strategy), so there are no duplicates.
 */
@Singleton
class ChatHistoryRecorder @Inject constructor(
    private val chatHistoryDao: ChatHistoryDao
) {

    /**
     * Records (or refreshes) one chat session in history.
     *
     * @param sessionId stable id of the chat session; when null a random one is
     * generated and returned so the caller can keep using it.
     * @param title short display title, e.g. the first user message truncated.
     * @param preview last message snippet shown as the subtitle.
     * @param timestamp recency used for Today/Yesterday/Last-7-days grouping.
     * @param isPinned whether the entry is pinned to the top. A pin already
     * stored on the row (set via HistoryViewModel.togglePinItem) is preserved:
     * the REPLACE insert must never silently unpin an entry.
     * @return the session id used for this row.
     */
    suspend fun recordSession(
        sessionId: String? = null,
        title: String,
        preview: String,
        timestamp: Long = System.currentTimeMillis(),
        isPinned: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val id = sessionId ?: UUID.randomUUID().toString()
        // REPLACE rewrites the whole row with isPinned=false by default, which
        // would silently unpin entries pinned via HistoryViewModel.togglePinItem.
        // Preserve the stored pin state instead (no DAO change needed).
        val pinned = isPinned ||
            chatHistoryDao.getAllHistory().firstOrNull { it.id == id }?.isPinned == true
        chatHistoryDao.insertItem(
            ChatHistoryItem(
                id = id,
                title = title,
                lastMessage = preview,
                timestamp = timestamp,
                isPinned = pinned
            )
        )
        id
    }

    /** Deletes a single session from history. Main-safe. */
    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        chatHistoryDao.deleteItem(sessionId)
    }

    /** Clears all recorded sessions. Main-safe. */
    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        chatHistoryDao.clearAll()
    }
}
