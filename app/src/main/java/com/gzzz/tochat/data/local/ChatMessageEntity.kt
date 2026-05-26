package com.gzzz.tochat.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    indices = [Index("sessionId")]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val createdAt: Long,
    val role: String,
    val content: String? = null,
    val imageSourcePath: String? = null,
    val imageResultPath: String? = null,
    val thumbnailPath: String? = null,
    val paramsJson: String? = null,
    val status: String = "success",
    val errorMessage: String? = null,
    val messageType: String = "image",
    val attachmentFileName: String? = null
)
