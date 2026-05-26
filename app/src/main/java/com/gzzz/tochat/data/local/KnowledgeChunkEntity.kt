package com.gzzz.tochat.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "knowledge_chunks",
    indices = [
        Index("documentId"),
        Index(value = ["documentId", "chunkIndex"], unique = true)
    ]
)
data class KnowledgeChunkEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val chunkIndex: Int,
    val text: String,
    val charStart: Int,
    val charEnd: Int,
    val tokenCount: Int,
    val createdAt: Long
)
