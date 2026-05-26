package com.gzzz.tochat.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GrokGenerateRequest(
    val model: String,
    val prompt: String,
    @SerialName("image_url") val imageUrl: String? = null,
    val n: Int = 1,
    val size: String = "1024x1024"
)

@Serializable
data class GrokTaskResponse(
    val id: String,
    val status: String,
    val output: GrokOutput? = null,
    @SerialName("error") val error: GrokError? = null
)

@Serializable
data class GrokOutput(
    val images: List<GrokImageData>? = null
)

@Serializable
data class GrokImageData(
    @SerialName("b64_json") val b64Json: String? = null,
    val url: String? = null,
    @SerialName("revised_prompt") val revisedPrompt: String? = null
)

@Serializable
data class GrokError(
    val code: String? = null,
    val message: String? = null
)
