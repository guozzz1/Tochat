package com.gzzz.tochat.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class GenerationRequest(
    val prompt: String,
    val negativePrompt: String? = null,
    val size: String = "1024x1024",
    val steps: Int? = null,
    val seed: Long? = null,
    val cfgScale: Float? = null,
    val batchSize: Int = 1,
    val sourceImagePath: String? = null,
    val providerId: String,
    val model: String
) {
    val paramsJson: String
        get() = kotlinx.serialization.json.Json.encodeToString(serializer(), this)
}
