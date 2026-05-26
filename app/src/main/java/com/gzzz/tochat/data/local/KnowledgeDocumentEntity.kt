package com.gzzz.tochat.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "knowledge_documents",
    indices = [
        Index("createdAt"),
        Index(value = ["contentHash"], unique = true)
    ]
)
data class KnowledgeDocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val fileName: String,
    val mimeType: String?,
    val contentHash: String,
    val charCount: Int,
    val chunkCount: Int,
    val status: String,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long
)
