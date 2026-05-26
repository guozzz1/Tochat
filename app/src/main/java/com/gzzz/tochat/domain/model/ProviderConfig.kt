package com.gzzz.tochat.domain.model

data class ServiceConfig(
    val displayName: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = ""
)

data class ProviderConfig(
    val id: String,
    val image: ServiceConfig,
    val chat: ServiceConfig,
    val isDefault: Boolean = false
)
