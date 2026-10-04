package com.salmanlaghari.pkai.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.salmanlaghari.pkai.data.model.ChatHistoryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ChatHistoryItem)

    @Query("SELECT * FROM chat_history ORDER BY isPinned DESC, timestamp DESC")
    fun getAllHistoryFlow(): Flow<List<ChatHistoryItem>>

    @Query("SELECT * FROM chat_history ORDER BY isPinned DESC, timestamp DESC")
    suspend fun getAllHistory(): List<ChatHistoryItem>

    @Query("DELETE FROM chat_history WHERE id = :itemId")
    suspend fun deleteItem(itemId: String)

    @Query("UPDATE chat_history SET title = :newTitle WHERE id = :itemId")
    suspend fun renameItem(itemId: String, newTitle: String)

    @Query("UPDATE chat_history SET isPinned = :isPinned WHERE id = :itemId")
    suspend fun setPinned(itemId: String, isPinned: Boolean)

    /**
     * Reads only the pin flag for one session — used by [ChatHistoryRecorder]
     * so pin preservation doesn't materialise the whole history table on
     * every recorded message.
     */
    @Query("SELECT isPinned FROM chat_history WHERE id = :itemId LIMIT 1")
    suspend fun isPinned(itemId: String): Boolean?

    @Query("DELETE FROM chat_history")
    suspend fun clearAll()
}
