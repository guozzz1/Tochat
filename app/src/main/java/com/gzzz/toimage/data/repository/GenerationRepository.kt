package com.gzzz.toimage.data.repository

import com.gzzz.toimage.data.local.ChatMessageDao
import com.gzzz.toimage.data.local.ChatMessageEntity
import com.gzzz.toimage.data.network.ConnectivityObserver
import com.gzzz.toimage.data.queue.TaskQueue
import com.gzzz.toimage.data.provider.ChatStreamEvent
import com.gzzz.toimage.data.provider.ImageProvider
import com.gzzz.toimage.data.provider.ImageProviderRegistry
import com.gzzz.toimage.data.remote.dto.ChatMessageDto
import com.gzzz.toimage.domain.model.GenerationRequest
import com.gzzz.toimage.domain.model.ProviderConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.util.Log
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class EnqueueError {
    NO_API_KEY,
    NO_NETWORK,
    PROVIDER_NOT_FOUND
}

enum class ChatError {
    NO_API_KEY,
    NO_NETWORK,
    PROVIDER_NOT_FOUND,
    NOT_SUPPORTED
}

@Singleton
class GenerationRepository @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val messageDao: ChatMessageDao,
    private val taskQueue: TaskQueue,
    private val connectivityObserver: ConnectivityObserver,
    private val providerRegistry: ImageProviderRegistry
) {
    val events = taskQueue.events

    suspend fun enqueue(
        sessionId: String,
        prompt: String,
        size: String = "1024x1024",
        providerId: String,
        model: String,
        apiKey: String?,
        sourceImagePath: String? = null
    ): EnqueueError? {
        // 前置校验
        if (apiKey.isNullOrBlank()) {
            return EnqueueError.NO_API_KEY
        }
        if (!connectivityObserver.isConnected) {
            return EnqueueError.NO_NETWORK
        }
        if (providerRegistry.get(providerId) == null) {
            return EnqueueError.PROVIDER_NOT_FOUND
        }

        // 创建用户消息
        val userMessage = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            createdAt = System.currentTimeMillis(),
            role = "user",
            content = prompt,
            status = "success",
            messageType = "image"
        )
        historyRepository.insertMessage(userMessage)

        // 创建 assistant pending 消息
        val request = GenerationRequest(
            prompt = prompt,
            size = size,
            providerId = providerId,
            model = model,
            sourceImagePath = sourceImagePath
        )
        val assistantMessage = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            createdAt = System.currentTimeMillis() + 1,
            role = "assistant",
            status = "pending",
            imageSourcePath = sourceImagePath,
            messageType = "image",
            paramsJson = Json.encodeToString(request)
        )
        historyRepository.insertMessage(assistantMessage)

        // 入队
        taskQueue.enqueue(assistantMessage.id, request)

        // 更新会话
        historyRepository.touchSession(sessionId)

        return null
    }

    suspend fun sendChat(
        sessionId: String,
        userText: String,
        providerId: String,
        model: String,
        apiKey: String?
    ): Flow<ChatStreamEvent> = flow {
        // 前置校验
        if (apiKey.isNullOrBlank()) {
            emit(ChatStreamEvent.Error(com.gzzz.toimage.data.provider.GenerationError.Unknown(IllegalStateException("未配置 API Key"))))
            return@flow
        }
        if (!connectivityObserver.isConnected) {
            emit(ChatStreamEvent.Error(com.gzzz.toimage.data.provider.GenerationError.NetworkUnavailable))
            return@flow
        }
        val provider = providerRegistry.get(providerId)
        if (provider == null) {
            emit(ChatStreamEvent.Error(com.gzzz.toimage.data.provider.GenerationError.Unknown(IllegalStateException("未找到对应的服务"))))
            return@flow
        }
        if (!provider.capabilities.supportsTextChat) {
            emit(ChatStreamEvent.Error(com.gzzz.toimage.data.provider.GenerationError.Unknown(UnsupportedOperationException("当前服务不支持文本对话"))))
            return@flow
        }

        // 插入用户消息
        val userMessage = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            createdAt = System.currentTimeMillis(),
            role = "user",
            content = userText,
            status = "success",
            messageType = "text"
        )
        historyRepository.insertMessage(userMessage)

        // 插入 assistant 消息（running 状态）
        val assistantMessageId = UUID.randomUUID().toString()
        val assistantMessage = ChatMessageEntity(
            id = assistantMessageId,
            sessionId = sessionId,
            createdAt = System.currentTimeMillis() + 1,
            role = "assistant",
            content = "",
            status = "running",
            messageType = "text"
        )
        historyRepository.insertMessage(assistantMessage)

        // 构建对话上下文
        val history = messageDao.getRecentTextMessages(sessionId, limit = 20)
            .reversed()
            .map { ChatMessageDto(role = it.role, content = it.content ?: "") }
        val messages = history + ChatMessageDto(role = "user", content = userText)

        // 更新会话
        historyRepository.touchSession(sessionId)

        // 调用 provider chat
        val fullText = StringBuilder()
        try {
            provider.chat(messages).collect { event ->
                when (event) {
                    is ChatStreamEvent.Delta -> {
                        fullText.append(event.text)
                        messageDao.updateContent(assistantMessageId, fullText.toString())
                        emit(event)
                    }
                    is ChatStreamEvent.Done -> {
                        messageDao.updateContent(assistantMessageId, event.fullText)
                        messageDao.updateStatus(assistantMessageId, "success")
                        Log.d(TAG, "chatFinalized(messageId=$assistantMessageId, status=success)")
                        emit(event)
                    }
                    is ChatStreamEvent.Error -> {
                        messageDao.updateStatusWithError(assistantMessageId, "failed", event.error.toString())
                        Log.d(TAG, "chatFinalized(messageId=$assistantMessageId, status=failed)")
                        emit(event)
                    }
                }
            }
        } catch (e: CancellationException) {
            messageDao.updateStatusWithError(assistantMessageId, "failed", "请求已中断，请重试")
            Log.d(TAG, "chatCancelled(messageId=$assistantMessageId)")
            throw e
        } catch (e: Exception) {
            messageDao.updateStatusWithError(assistantMessageId, "failed", e.localizedMessage)
            emit(ChatStreamEvent.Error(com.gzzz.toimage.data.provider.GenerationError.Unknown(e)))
        }
    }

    fun cancel() {
        taskQueue.cancel()
    }

    fun configureProvider(config: ProviderConfig) {
        providerRegistry.get(config.id)?.configure(config.image, config.chat)
    }

    fun getProvider(providerId: String): ImageProvider? = providerRegistry.get(providerId)

    fun isConnected(): Boolean = connectivityObserver.isConnected

    suspend fun recoverRunningTextMessages(sessionId: String) {
        val count = messageDao.getRunningTextCount(sessionId)
        if (count > 0) {
            messageDao.markRunningTextAsStatus(sessionId, "failed", "请求已中断，请重试")
            Log.d(TAG, "recoverRunningTextMessages(sessionId=$sessionId, count=$count)")
        }
    }

    private companion object {
        const val TAG = "GenerationRepository"
    }
}
