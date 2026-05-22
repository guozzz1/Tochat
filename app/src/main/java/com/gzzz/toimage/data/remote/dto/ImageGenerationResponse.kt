package com.gzzz.toimage.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ImageGenerationResponse(
    val created: Long? = null,
    val data: List<ImageData>
)

@Serializable
data class ImageData(
    @SerialName("b64_json") val b64Json: String? = null,
    val url: String? = null,
    @SerialName("revised_prompt") val revisedPrompt: String? = null
)
