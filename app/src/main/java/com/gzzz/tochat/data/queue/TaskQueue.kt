package com.gzzz.tochat.data.queue

import android.content.Context
import com.gzzz.tochat.data.local.ChatMessageDao
import com.gzzz.tochat.data.provider.GenerationError
import com.gzzz.tochat.data.provider.GenerationProgress
import com.gzzz.tochat.data.provider.ImageProviderRegistry
import com.gzzz.tochat.data.storage.StorageCleaner
import com.gzzz.tochat.domain.model.GenerationRequest
import com.gzzz.tochat.service.GenerationService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

data class TaskEvent(
    val messageId: String,
    val progress: GenerationProgress
)

@Singleton
class TaskQueue @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageDao: ChatMessageDao,
    private val providerRegistry: ImageProviderRegistry,
    private val storageCleaner: StorageCleaner
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<TaskEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    @Volatile
    private var currentJob: Job? = null

    @Volatile
    private var currentMessageId: String? = null

    fun enqueue(messageId: String, request: GenerationRequest) {
        val job = scope.launch {
            mutex.withLock {
                currentMessageId = messageId
                // 启动前台服务
                GenerationService.start(context, request.prompt)
                try {
                    processTask(messageId, request)
                } finally {
                    currentMessageId = null
                    GenerationService.stop(context)
                }
            }
        }
        currentJob = job
    }

    private suspend fun processTask(messageId: String, request: GenerationRequest) {
        val provider = providerRegistry.get(request.providerId)
        if (provider == null) {
            messageDao.updateStatusWithError(messageId, "failed", "未找到 provider: ${request.providerId}")
            _events.emit(TaskEvent(messageId, GenerationProgress.Failed(
                GenerationError.Unknown(IllegalStateException("Provider not found: ${request.providerId}"))
            )))
            return
        }

        try {
            messageDao.updateStatus(messageId, "running")
            _events.emit(TaskEvent(messageId, GenerationProgress.Starting))

            provider.generate(request).collect { progress ->
                _events.emit(TaskEvent(messageId, progress))

                when (progress) {
                    is GenerationProgress.Success -> {
                        messageDao.updateResult(
                            id = messageId,
                            path = progress.result.imagePath,
                            thumbPath = progress.result.thumbnailPath,
                            status = "success"
                        )
                        storageCleaner.cleanIfNeeded()
                    }
                    is GenerationProgress.Failed -> {
                        if (progress.error is GenerationError.Cancelled) {
                            messageDao.updateStatus(messageId, "cancelled")
                        } else {
                            val errorMsg = when (progress.error) {
                                is GenerationError.NetworkUnavailable -> "网络不可用"
                                is GenerationError.ApiError -> progress.error.message
                                is GenerationError.Timeout -> "生成超时"
                                is GenerationError.ContentRejected -> "内容不合规"
                                is GenerationError.Cancelled -> "已取消"
                                is GenerationError.Unknown -> progress.error.throwable.localizedMessage
                            }
                            messageDao.updateStatusWithError(messageId, "failed", errorMsg)
                        }
                    }
                    else -> {}
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            messageDao.updateStatus(messageId, "cancelled")
            _events.emit(TaskEvent(messageId, GenerationProgress.Failed(GenerationError.Cancelled)))
            throw e
        } catch (e: Exception) {
            messageDao.updateStatusWithError(messageId, "failed", e.localizedMessage)
            _events.emit(TaskEvent(messageId, GenerationProgress.Failed(GenerationError.Unknown(e))))
        }
    }

    fun cancel() {
        currentJob?.cancel()
        currentJob = null
        GenerationService.stop(context)
    }

    suspend fun restoreUnfinishedTasks() {
        val unfinished = messageDao.getUnfinished()
        for (msg in unfinished) {
            messageDao.updateStatusWithError(msg.id, "failed", "应用重启，任务未完成")
        }
    }
}
