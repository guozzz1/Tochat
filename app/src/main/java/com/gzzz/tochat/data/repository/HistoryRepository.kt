package com.gzzz.tochat.data.repository

import com.gzzz.tochat.data.local.ChatMessageDao
import com.gzzz.tochat.data.local.ChatMessageEntity
import com.gzzz.tochat.data.local.ChatSessionDao
import com.gzzz.tochat.data.local.ChatSessionEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val sessionDao: ChatSessionDao,
    private val messageDao: ChatMessageDao
) {
    // --- Sessions ---

    fun observeSessions(): Flow<List<ChatSessionEntity>> = sessionDao.observeAll()

    fun searchSessions(query: String): Flow<List<ChatSessionEntity>> = sessionDao.search(query)

    suspend fun getSession(id: String): ChatSessionEntity? = sessionDao.getById(id)

    fun observeSession(id: String): Flow<ChatSessionEntity?> = sessionDao.observeById(id)

    suspend fun createSession(
        providerId: String,
        model: String,
        firstPrompt: String
    ): ChatSessionEntity {
        val session = ChatSessionEntity(
            id = UUID.randomUUID().toString(),
            title = firstPrompt.take(30) + if (firstPrompt.length > 30) "..." else "",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            providerId = providerId,
            model = model
        )
        sessionDao.insert(session)
        return session
    }

    suspend fun updateSession(session: ChatSessionEntity) {
        sessionDao.update(session)
    }

    suspend fun renameSession(sessionId: String, title: String): ChatSessionEntity? {
        val session = sessionDao.getById(sessionId) ?: return null
        val renamed = session.copy(title = title)
        sessionDao.update(renamed)
        return renamed
    }

    suspend fun deleteSession(sessionId: String) {
        messageDao.deleteBySession(sessionId)
        sessionDao.deleteById(sessionId)
    }

    suspend fun deleteAllSessions() {
        messageDao.deleteAll()
        sessionDao.deleteAll()
    }

    suspend fun updateSessionThumbnail(sessionId: String, thumbnailPath: String?) {
        sessionDao.updateThumbnail(sessionId, thumbnailPath)
    }

    suspend fun touchSession(sessionId: String) {
        val session = sessionDao.getById(sessionId) ?: return
        sessionDao.update(session.copy(updatedAt = System.currentTimeMillis()))
    }

    // --- Messages ---

    fun observeMessages(sessionId: String): Flow<List<ChatMessageEntity>> =
        messageDao.observeBySession(sessionId)

    suspend fun getMessages(sessionId: String): List<ChatMessageEntity> =
        messageDao.getBySession(sessionId)

    suspend fun getMessage(id: String): ChatMessageEntity? = messageDao.getById(id)

    suspend fun insertMessage(message: ChatMessageEntity) {
        messageDao.insert(message)
    }

    suspend fun insertMessages(messages: List<ChatMessageEntity>) {
        messageDao.insertAll(messages)
    }

    suspend fun updateMessage(message: ChatMessageEntity) {
        messageDao.update(message)
    }

    suspend fun deleteMessage(id: String) {
        val msg = messageDao.getById(id) ?: return
        messageDao.delete(msg)
    }

    suspend fun getLatestResultMessage(sessionId: String): ChatMessageEntity? =
        messageDao.getLatestResultMessage(sessionId)

    suspend fun getResultImageCount(): Int = messageDao.getResultImageCount()

    suspend fun getOldestResultMessages(limit: Int): List<ChatMessageEntity> =
        messageDao.getOldestResultMessages(limit)
}
