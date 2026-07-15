package com.salmanlaghari.pkai.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase
import com.salmanlaghari.pkai.data.model.ChatMessage
import com.salmanlaghari.pkai.data.model.ChatHistoryItem

@Database(entities = [AppLog::class, ChatMessage::class, ChatHistoryItem::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appLogDao(): AppLogDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun chatHistoryDao(): ChatHistoryDao
}
