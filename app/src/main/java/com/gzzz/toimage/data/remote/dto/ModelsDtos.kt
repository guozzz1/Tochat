package com.gzzz.toimage.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ModelsResponse(val data: List<ModelItem> = emptyList())

@Serializable
data class ModelItem(val id: String)
