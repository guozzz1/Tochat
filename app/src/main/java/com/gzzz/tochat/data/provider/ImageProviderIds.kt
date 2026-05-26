package com.gzzz.tochat.data.provider

import com.gzzz.tochat.data.local.ApiConfigEntity

const val PROVIDER_GPT_IMAGE = "gpt-image-2"
const val PROVIDER_GROK = "grok"

fun ApiConfigEntity.effectiveImageProviderId(): String = providerId ?: PROVIDER_GPT_IMAGE

fun imageProviderDisplayName(providerId: String?): String = when (providerId ?: PROVIDER_GPT_IMAGE) {
    PROVIDER_GROK -> "Grok / xAI"
    else -> "OpenAI / GPT"
}
