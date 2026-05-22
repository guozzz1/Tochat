package com.gzzz.toimage.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val providerId: String,
    val model: String,
    val thumbnailPath: String? = null
)
