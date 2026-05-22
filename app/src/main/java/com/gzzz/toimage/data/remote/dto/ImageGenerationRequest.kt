package com.gzzz.toimage.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ImageGenerationRequest(
    val model: String,
    val prompt: String,
    val n: Int = 1,
    val size: String = "1024x1024",
    @SerialName("response_format") val responseFormat: String? = null
)
