package com.gzzz.tochat.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgeDao {
    @Query("SELECT * FROM knowledge_documents ORDER BY createdAt DESC")
    fun observeDocuments(): Flow<List<KnowledgeDocumentEntity>>

    @Query("SELECT * FROM knowledge_documents WHERE id = :id")
    suspend fun getDocument(id: String): KnowledgeDocumentEntity?

    @Query("SELECT * FROM knowledge_documents WHERE id IN (:ids)")
    suspend fun getDocumentsByIds(ids: List<String>): List<KnowledgeDocumentEntity>

    @Query("SELECT * FROM knowledge_documents WHERE contentHash = :hash LIMIT 1")
    suspend fun getDocumentByHash(hash: String): KnowledgeDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: KnowledgeDocumentEntity)

    @Update
    suspend fun updateDocument(document: KnowledgeDocumentEntity)

    @Query("DELETE FROM knowledge_documents WHERE id = :id")
    suspend fun deleteDocument(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<KnowledgeChunkEntity>)

    @Query("DELETE FROM knowledge_chunks WHERE documentId = :documentId")
    suspend fun deleteChunksByDocumentId(documentId: String)

    @Query("SELECT * FROM knowledge_chunks WHERE documentId IN (:documentIds) ORDER BY documentId ASC, chunkIndex ASC")
    suspend fun getChunksByDocumentIds(documentIds: List<String>): List<KnowledgeChunkEntity>
}
