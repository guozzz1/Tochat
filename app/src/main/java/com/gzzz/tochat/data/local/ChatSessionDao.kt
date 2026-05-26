package com.gzzz.tochat.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {

    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    suspend fun getAll(): List<ChatSessionEntity>

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun getById(id: String): ChatSessionEntity?

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    fun observeById(id: String): Flow<ChatSessionEntity?>

    @Query("SELECT * FROM chat_sessions WHERE title LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun search(query: String): Flow<List<ChatSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ChatSessionEntity)

    @Update
    suspend fun update(session: ChatSessionEntity)

    @Delete
    suspend fun delete(session: ChatSessionEntity)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAll()

    @Query("UPDATE chat_sessions SET thumbnailPath = :thumbnailPath WHERE id = :sessionId")
    suspend fun updateThumbnail(sessionId: String, thumbnailPath: String?)
}
