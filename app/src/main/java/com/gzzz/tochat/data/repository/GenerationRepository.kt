package com.gzzz.tochat.data.repository

import com.gzzz.tochat.data.local.ChatMessageDao
import com.gzzz.tochat.data.local.ChatMessageEntity
import com.gzzz.tochat.data.network.ConnectivityObserver
import com.gzzz.tochat.data.queue.TaskQueue
import com.gzzz.tochat.data.provider.ChatStreamEvent
import com.gzzz.tochat.data.provider.GenerationError
import com.gzzz.tochat.data.provider.ImageProvider
import com.gzzz.tochat.data.provider.ImageProviderRegistry
import com.gzzz.tochat.data.provider.OpenAiChatClient
import com.gzzz.tochat.data.provider.toChineseMessage
import com.gzzz.tochat.data.remote.dto.ChatMessageDto
import com.gzzz.tochat.domain.model.GenerationRequest
import com.gzzz.tochat.domain.model.ProviderConfig
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

data class RoundtableParticipant(
    val configId: String,
    val configName: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val chatPath: String = "v1/chat/completions",
    val chatProtocol: String = "chat_completions"
) {
    val key: String get() = "$configId::$model"
}

sealed class RoundtableRunEvent {
    object Started : RoundtableRunEvent()
    data class TurnStarted(val round: Int, val participant: RoundtableParticipant) : RoundtableRunEvent()
    data class TurnCompleted(val round: Int, val participant: RoundtableParticipant) : RoundtableRunEvent()
    data class SummaryStarted(val participant: RoundtableParticipant) : RoundtableRunEvent()
    object Completed : RoundtableRunEvent()
    data class Error(val message: String) : RoundtableRunEvent()
}

private data class StopParseResult(
    val visibleContent: String,
    val stopReason: String
)

@Singleton
class GenerationRepository @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val messageDao: ChatMessageDao,
    private val taskQueue: TaskQueue,
    private val connectivityObserver: ConnectivityObserver,
    private val providerRegistry: ImageProviderRegistry,
    private val openAiChatClient: OpenAiChatClient
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
        displayText: String = userText,
        attachmentFileName: String? = null,
        providerId: String,
        model: String,
        apiKey: String?
    ): Flow<ChatStreamEvent> = flow {
        // 前置校验
        if (apiKey.isNullOrBlank()) {
            emit(ChatStreamEvent.Error(com.gzzz.tochat.data.provider.GenerationError.Unknown(IllegalStateException("未配置 API Key"))))
            return@flow
        }
        if (!connectivityObserver.isConnected) {
            emit(ChatStreamEvent.Error(com.gzzz.tochat.data.provider.GenerationError.NetworkUnavailable))
            return@flow
        }
        val provider = providerRegistry.get(providerId)
        if (provider == null) {
            emit(ChatStreamEvent.Error(com.gzzz.tochat.data.provider.GenerationError.Unknown(IllegalStateException("未找到对应的服务"))))
            return@flow
        }
        if (!provider.capabilities.supportsTextChat) {
            emit(ChatStreamEvent.Error(com.gzzz.tochat.data.provider.GenerationError.Unknown(UnsupportedOperationException("当前服务不支持文本对话"))))
            return@flow
        }

        // 插入用户消息
        val userMessage = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            createdAt = System.currentTimeMillis(),
            role = "user",
            content = displayText,
            status = "success",
            messageType = "text",
            attachmentFileName = attachmentFileName
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
            emit(ChatStreamEvent.Error(com.gzzz.tochat.data.provider.GenerationError.Unknown(e)))
        }
    }

    suspend fun sendRoundtable(
        sessionId: String,
        userText: String,
        displayText: String = userText,
        attachmentFileName: String? = null,
        participants: List<RoundtableParticipant>,
        maxRounds: Int
    ): Flow<RoundtableRunEvent> = flow {
        if (!connectivityObserver.isConnected) {
            emit(RoundtableRunEvent.Error("网络不可用，请检查网络连接"))
            return@flow
        }
        if (participants.size !in 2..4) {
            emit(RoundtableRunEvent.Error("请选择 2-4 个对话模型"))
            return@flow
        }
        val invalidParticipant = participants.firstOrNull {
            it.apiKey.isBlank() || it.baseUrl.isBlank() || !(it.baseUrl.startsWith("http://") || it.baseUrl.startsWith("https://"))
        }
        if (invalidParticipant != null) {
            emit(RoundtableRunEvent.Error("${invalidParticipant.configName} 配置不完整"))
            return@flow
        }

        emit(RoundtableRunEvent.Started)

        val userMessage = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            createdAt = System.currentTimeMillis(),
            role = "user",
            content = displayText,
            status = "success",
            messageType = "roundtable",
            attachmentFileName = attachmentFileName
        )
        historyRepository.insertMessage(userMessage)
        historyRepository.touchSession(sessionId)

        val recentContext = messageDao.getRecentTextLikeMessages(sessionId, limit = 20)
            .reversed()
            .filter { it.id != userMessage.id }
            .joinToString("\n\n") { message ->
                val role = if (message.role == "user") "用户" else "助手"
                "$role：${message.content.orEmpty()}"
            }
        val transcript = StringBuilder()
        val stoppedParticipants = mutableSetOf<String>()
        var currentMessageId: String? = null

        try {
            for (round in 1..maxRounds.coerceIn(1, 10)) {
                for (participant in participants) {
                    if (participant.key in stoppedParticipants) continue

                    emit(RoundtableRunEvent.TurnStarted(round, participant))
                    val title = "### 第 ${round} 轮 · ${participant.configName} / ${participant.model}\n\n"
                    val assistantMessageId = UUID.randomUUID().toString()
                    currentMessageId = assistantMessageId
                    historyRepository.insertMessage(
                        ChatMessageEntity(
                            id = assistantMessageId,
                            sessionId = sessionId,
                            createdAt = System.currentTimeMillis(),
                            role = "assistant",
                            content = title,
                            status = "running",
                            messageType = "roundtable"
                        )
                    )

                    val fullText = StringBuilder()
                    val messages = listOf(
                        ChatMessageDto(role = "system", content = buildRoundtableSystemPrompt()),
                        ChatMessageDto(
                            role = "user",
                            content = buildRoundtableTurnPrompt(
                                userText = userText,
                                recentContext = recentContext,
                                transcript = transcript.toString(),
                                participant = participant,
                                participants = participants,
                                round = round,
                                maxRounds = maxRounds
                            )
                        )
                    )

                    var turnFailed = false
                    openAiChatClient.chat(
                        baseUrl = participant.baseUrl,
                        apiKey = participant.apiKey,
                        model = participant.model,
                        messages = messages,
                        chatPath = participant.chatPath,
                        chatProtocol = participant.chatProtocol
                    ).collect { event ->
                        when (event) {
                            is ChatStreamEvent.Delta -> {
                                fullText.append(event.text)
                                messageDao.updateContent(assistantMessageId, title + fullText.toString())
                            }
                            is ChatStreamEvent.Done -> {
                                val parsed = parseStopMarker(event.fullText)
                                messageDao.updateContent(assistantMessageId, title + parsed.visibleContent)
                                messageDao.updateStatus(assistantMessageId, "success")
                                transcript.append("第 ${round} 轮 ${participant.configName} / ${participant.model}：\n")
                                transcript.append(parsed.visibleContent)
                                transcript.append("\n\n")
                                if (!parsed.stopReason.equals("continue", ignoreCase = true)) {
                                    stoppedParticipants.add(participant.key)
                                }
                                currentMessageId = null
                                emit(RoundtableRunEvent.TurnCompleted(round, participant))
                            }
                            is ChatStreamEvent.Error -> {
                                turnFailed = true
                                stoppedParticipants.add(participant.key)
                                val message = event.error.toChineseMessage().ifBlank { "讨论请求已中断" }
                                messageDao.updateStatusWithError(assistantMessageId, "failed", message)
                                currentMessageId = null
                                emit(RoundtableRunEvent.Error("${participant.configName} 发言失败：$message"))
                            }
                        }
                    }

                    if (turnFailed) continue
                }

                if (stoppedParticipants.size == participants.size) break
            }

            val leader = participants.first()
            emit(RoundtableRunEvent.SummaryStarted(leader))
            val summaryTitle = "### Leader 总结 · ${leader.configName} / ${leader.model}\n\n"
            val summaryMessageId = UUID.randomUUID().toString()
            currentMessageId = summaryMessageId
            historyRepository.insertMessage(
                ChatMessageEntity(
                    id = summaryMessageId,
                    sessionId = sessionId,
                    createdAt = System.currentTimeMillis(),
                    role = "assistant",
                    content = summaryTitle,
                    status = "running",
                    messageType = "roundtable"
                )
            )

            val summaryText = StringBuilder()
            openAiChatClient.chat(
                baseUrl = leader.baseUrl,
                apiKey = leader.apiKey,
                model = leader.model,
                chatPath = leader.chatPath,
                chatProtocol = leader.chatProtocol,
                messages = listOf(
                    ChatMessageDto(role = "system", content = buildRoundtableSummarySystemPrompt()),
                    ChatMessageDto(
                        role = "user",
                        content = buildRoundtableSummaryPrompt(
                            userText = userText,
                            recentContext = recentContext,
                            transcript = transcript.toString()
                        )
                    )
                )
            ).collect { event ->
                when (event) {
                    is ChatStreamEvent.Delta -> {
                        summaryText.append(event.text)
                        messageDao.updateContent(summaryMessageId, summaryTitle + summaryText.toString())
                    }
                    is ChatStreamEvent.Done -> {
                        messageDao.updateContent(summaryMessageId, summaryTitle + event.fullText)
                        messageDao.updateStatus(summaryMessageId, "success")
                        currentMessageId = null
                    }
                    is ChatStreamEvent.Error -> {
                        val message = event.error.toChineseMessage().ifBlank { "总结请求已中断" }
                        messageDao.updateStatusWithError(summaryMessageId, "failed", message)
                        currentMessageId = null
                        emit(RoundtableRunEvent.Error("Leader 总结失败：$message"))
                    }
                }
            }

            historyRepository.touchSession(sessionId)
            emit(RoundtableRunEvent.Completed)
        } catch (e: CancellationException) {
            currentMessageId?.let { messageDao.updateStatusWithError(it, "failed", "讨论已中断，请重试") }
            throw e
        } catch (e: Exception) {
            currentMessageId?.let { messageDao.updateStatusWithError(it, "failed", e.localizedMessage) }
            emit(RoundtableRunEvent.Error(e.localizedMessage ?: "圆桌讨论失败"))
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

    private fun buildRoundtableSystemPrompt(): String = """
        你正在参与一场多模型串行圆桌讨论。
        请基于用户问题和前面模型的发言，补充、修正、反驳或综合，不要简单重复。
        最后一行必须且只能输出一个停止标记：
        [STOP_REASON: continue] 表示继续下一轮仍有明显价值。
        [STOP_REASON: answered] 或其他简短英文/中文原因表示你认为讨论已经充分。
    """.trimIndent()

    private fun buildRoundtableTurnPrompt(
        userText: String,
        recentContext: String,
        transcript: String,
        participant: RoundtableParticipant,
        participants: List<RoundtableParticipant>,
        round: Int,
        maxRounds: Int
    ): String = buildString {
        appendLine("【当前发言模型】")
        appendLine("${participant.configName} / ${participant.model}")
        appendLine()
        appendLine("【参与模型顺序】")
        participants.forEachIndexed { index, item ->
            appendLine("${index + 1}. ${item.configName} / ${item.model}${if (index == 0) "（Leader）" else ""}")
        }
        appendLine()
        appendLine("【轮次】")
        appendLine("第 $round 轮 / 最多 $maxRounds 轮")
        appendLine()
        if (recentContext.isNotBlank()) {
            appendLine("【当前会话上下文摘要】")
            appendLine(recentContext)
            appendLine()
        }
        appendLine("【用户问题】")
        appendLine(userText)
        appendLine()
        appendLine("【历史讨论记录】")
        appendLine(transcript.ifBlank { "暂无，当前是第一位发言。" })
        appendLine()
        appendLine("请给出你的本轮发言。最后一行必须输出 [STOP_REASON: continue] 或 [STOP_REASON: 原因]。")
    }

    private fun buildRoundtableSummarySystemPrompt(): String = """
        你是本次圆桌讨论的 Leader。
        请基于完整讨论记录输出最终总结，包含核心结论、各方观点要点、推荐方案、争议或待确认事项。
        不要输出 STOP_REASON 标记。
    """.trimIndent()

    private fun buildRoundtableSummaryPrompt(
        userText: String,
        recentContext: String,
        transcript: String
    ): String = buildString {
        if (recentContext.isNotBlank()) {
            appendLine("【当前会话上下文摘要】")
            appendLine(recentContext)
            appendLine()
        }
        appendLine("【用户问题】")
        appendLine(userText)
        appendLine()
        appendLine("【圆桌讨论记录】")
        appendLine(transcript.ifBlank { "没有可用讨论记录，请直接基于用户问题总结。" })
    }

    private fun parseStopMarker(content: String): StopParseResult {
        val lines = content.lines()
        val lastIndex = lines.indexOfLast { it.isNotBlank() }
        if (lastIndex == -1) return StopParseResult(content.trim(), "continue")

        val lastLine = lines[lastIndex].trim()
        val match = Regex("^\\[STOP_REASON:\\s*(.+?)\\]$").find(lastLine)
            ?: return StopParseResult(content.trim(), "continue")

        val visible = lines.filterIndexed { index, _ -> index != lastIndex }
            .joinToString("\n")
            .trim()
        return StopParseResult(visible, match.groupValues[1].trim().ifBlank { "continue" })
    }

    private companion object {
        const val TAG = "GenerationRepository"
    }
}
