package com.gzzz.tochat.data.provider

import com.gzzz.tochat.data.remote.dto.ChatMessageDto
import com.gzzz.tochat.domain.model.GenerationRequest
import com.gzzz.tochat.domain.model.GenerationResult
import com.gzzz.tochat.domain.model.ServiceConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface ImageProvider {
    val id: String
    val displayName: String
    val capabilities: Capabilities
    val isConfigured: Boolean

    fun configure(image: ServiceConfig, chat: ServiceConfig)
    suspend fun generate(request: GenerationRequest): Flow<GenerationProgress>

    suspend fun chat(messages: List<ChatMessageDto>): Flow<ChatStreamEvent> = flow {
        emit(ChatStreamEvent.Error(GenerationError.Unknown(UnsupportedOperationException("不支持文本对话"))))
    }
}

data class Capabilities(
    val supportsImg2Img: Boolean = false,
    val supportsSeed: Boolean = false,
    val supportsSteps: Boolean = false,
    val supportsNegativePrompt: Boolean = false,
    val supportedSizes: List<String> = listOf("1024x1024"),
    val maxBatchSize: Int = 1,
    val supportsTextChat: Boolean = false,
    val supportsStreaming: Boolean = false
)

sealed class GenerationProgress {
    object Starting : GenerationProgress()
    data class Generating(val percent: Int? = null) : GenerationProgress()
    data class Success(val result: GenerationResult) : GenerationProgress()
    data class Failed(val error: GenerationError) : GenerationProgress()
}

sealed class ChatStreamEvent {
    data class Delta(val text: String) : ChatStreamEvent()
    data class Done(val fullText: String) : ChatStreamEvent()
    data class Error(val error: GenerationError) : ChatStreamEvent()
}
