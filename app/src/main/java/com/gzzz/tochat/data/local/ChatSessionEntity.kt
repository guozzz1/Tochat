package com.gzzz.tochat.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_sessions",
    indices = [Index("parentSessionId")]
)
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val providerId: String,
    val model: String,
    val thumbnailPath: String? = null,
    val parentSessionId: String? = null,
    val branchedFromMessageId: String? = null,
    val branchedAt: Long? = null
)
