package com.gzzz.tochat.data.provider

interface ImageProviderRegistry {
    fun get(providerId: String): ImageProvider?
    fun all(): List<ImageProvider>
    fun register(provider: ImageProvider)
}
