package com.gzzz.tochat.domain.model

data class GenerationResult(
    val imagePath: String,
    val thumbnailPath: String? = null,
    val revisedPrompt: String? = null
)
