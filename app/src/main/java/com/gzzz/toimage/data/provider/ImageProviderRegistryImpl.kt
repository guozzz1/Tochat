package com.gzzz.toimage.data.provider

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageProviderRegistryImpl @Inject constructor() : ImageProviderRegistry {

    private val providers = mutableMapOf<String, ImageProvider>()

    override fun get(providerId: String): ImageProvider? = providers[providerId]

    override fun all(): List<ImageProvider> = providers.values.toList()

    override fun register(provider: ImageProvider) {
        providers[provider.id] = provider
    }
}
