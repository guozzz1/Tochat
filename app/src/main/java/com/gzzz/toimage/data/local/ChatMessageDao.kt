package com.gzzz.toimage.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeBySession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun getBySession(sessionId: String): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE id = :id")
    suspend fun getById(id: String): ChatMessageEntity?

    @Query("SELECT * FROM chat_messages WHERE status IN ('pending', 'running')")
    suspend fun getUnfinished(): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<ChatMessageEntity>)

    @Update
    suspend fun update(message: ChatMessageEntity)

    @Delete
    suspend fun delete(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll()

    @Query("UPDATE chat_messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE chat_messages SET status = :status, errorMessage = :error WHERE id = :id")
    suspend fun updateStatusWithError(id: String, status: String, error: String?)

    @Query("UPDATE chat_messages SET imageResultPath = :path, thumbnailPath = :thumbPath, status = :status WHERE id = :id")
    suspend fun updateResult(id: String, path: String, thumbPath: String?, status: String)

    @Query("UPDATE chat_messages SET content = :content WHERE id = :id")
    suspend fun updateContent(id: String, content: String)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId AND messageType = 'text' AND status = 'success' ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentTextMessages(sessionId: String, limit: Int): List<ChatMessageEntity>

    @Query("""
        SELECT * FROM chat_messages
        WHERE sessionId = :sessionId AND status = 'success' AND imageResultPath IS NOT NULL
        ORDER BY createdAt DESC LIMIT 1
    """)
    suspend fun getLatestResultMessage(sessionId: String): ChatMessageEntity?

    @Query("SELECT COUNT(*) FROM chat_messages WHERE imageResultPath IS NOT NULL AND status = 'success'")
    suspend fun getResultImageCount(): Int

    @Query("""
        SELECT * FROM chat_messages
        WHERE imageResultPath IS NOT NULL AND status = 'success'
        ORDER BY createdAt ASC LIMIT :limit
    """)
    suspend fun getOldestResultMessages(limit: Int): List<ChatMessageEntity>

    @Query("""
        UPDATE chat_messages SET status = :status, errorMessage = :errorMessage
        WHERE sessionId = :sessionId AND messageType = 'text' AND status = 'running'
    """)
    suspend fun markRunningTextAsStatus(sessionId: String, status: String, errorMessage: String?)

    @Query("""
        SELECT COUNT(*) FROM chat_messages
        WHERE sessionId = :sessionId AND messageType = 'text' AND status = 'running'
    """)
    suspend fun getRunningTextCount(sessionId: String): Int
}
