package com.salmanlaghari.pkai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "chat_history")
data class ChatHistoryItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val lastMessage: String,
    val timestamp: Long,
    val isPinned: Boolean = false
)
