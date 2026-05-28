package com.gzzz.tochat.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val stream: Boolean = true,
    val temperature: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null
)

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String
)

@Serializable
data class ChatCompletionChunk(
    val id: String? = null,
    @SerialName("object") val objectType: String? = null,
    val choices: List<ChunkChoice> = emptyList()
)

@Serializable
data class ChunkChoice(
    val index: Int = 0,
    val delta: Delta,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class Delta(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val choices: List<ChatChoice> = emptyList()
)

@Serializable
data class ChatChoice(
    val index: Int = 0,
    val message: ChatMessageDto,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ResponsesApiRequest(
    val model: String,
    val input: List<ResponsesInputMessageDto>,
    val stream: Boolean = false
)

@Serializable
data class ResponsesInputMessageDto(
    val role: String,
    val content: String
)

@Serializable
data class ResponsesApiResponse(
    val id: String? = null,
    @SerialName("output_text") val outputText: String? = null,
    val output: List<ResponsesOutputItem> = emptyList()
)

@Serializable
data class ResponsesOutputItem(
    val type: String? = null,
    val role: String? = null,
    val content: List<ResponsesContentItem> = emptyList()
)

@Serializable
data class ResponsesContentItem(
    val type: String? = null,
    val text: String? = null
)
