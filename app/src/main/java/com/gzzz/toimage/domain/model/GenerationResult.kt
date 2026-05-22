package com.gzzz.toimage.domain.model

data class GenerationResult(
    val imagePath: String,
    val thumbnailPath: String? = null,
    val revisedPrompt: String? = null
)
