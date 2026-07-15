package com.salmanlaghari.pkai.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase
import com.salmanlaghari.pkai.data.model.ChatMessage

@Database(entities = [AppLog::class, ChatMessage::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appLogDao(): AppLogDao
    abstract fun chatMessageDao(): ChatMessageDao
}
