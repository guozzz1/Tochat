package com.gzzz.toimage.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.gzzz.toimage.data.security.SecureStorage
import com.gzzz.toimage.domain.model.ProviderConfig
import com.gzzz.toimage.domain.model.ServiceConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("toimage_settings", Context.MODE_PRIVATE)

    private val _currentProvider = MutableStateFlow(loadCurrentProvider())
    val currentProvider: StateFlow<ProviderConfig?> = _currentProvider.asStateFlow()

    fun getProviderConfig(providerId: String): ProviderConfig? {
        val imgBaseUrl = secureStorage.getBaseUrl(providerId) ?: return null
        val imgApiKey = secureStorage.getApiKey(providerId) ?: return null
        val imgModel = prefs.getString("model_$providerId", null) ?: return null
        val imgName = prefs.getString("name_$providerId", providerId) ?: providerId

        val chatBaseUrl = secureStorage.getChatBaseUrl(providerId).orEmpty()
        val chatApiKey = secureStorage.getChatApiKey(providerId).orEmpty()
        val chatModel = prefs.getString("chat_model_$providerId", "") ?: ""
        val chatName = prefs.getString("chat_name_$providerId", "") ?: ""

        return ProviderConfig(
            id = providerId,
            image = ServiceConfig(
                displayName = imgName,
                baseUrl = imgBaseUrl,
                apiKey = imgApiKey,
                model = imgModel
            ),
            chat = ServiceConfig(
                displayName = chatName,
                baseUrl = chatBaseUrl,
                apiKey = chatApiKey,
                model = chatModel
            ),
            isDefault = prefs.getString("default_provider", null) == providerId
        )
    }

    fun getAllProviderConfigs(): List<ProviderConfig> {
        val ids = getSavedProviderIds()
        return ids.mapNotNull { getProviderConfig(it) }
    }

    fun saveProviderConfig(config: ProviderConfig) {
        secureStorage.saveBaseUrl(config.id, config.image.baseUrl)
        secureStorage.saveApiKey(config.id, config.image.apiKey)
        secureStorage.saveChatBaseUrl(config.id, config.chat.baseUrl)
        secureStorage.saveChatApiKey(config.id, config.chat.apiKey)
        prefs.edit()
            .putString("model_${config.id}", config.image.model)
            .putString("name_${config.id}", config.image.displayName)
            .putString("chat_model_${config.id}", config.chat.model)
            .putString("chat_name_${config.id}", config.chat.displayName)
            .apply()
        addProviderId(config.id)
        if (config.isDefault) {
            prefs.edit().putString("default_provider", config.id).apply()
        }
        if (_currentProvider.value?.id == config.id) {
            _currentProvider.value = config
        }
    }

    fun setCurrentProvider(providerId: String) {
        prefs.edit().putString("default_provider", providerId).apply()
        _currentProvider.value = getProviderConfig(providerId)
    }

    fun removeProvider(providerId: String) {
        secureStorage.removeApiKey(providerId)
        secureStorage.removeBaseUrl(providerId)
        secureStorage.removeChatApiKey(providerId)
        secureStorage.removeChatBaseUrl(providerId)
        prefs.edit()
            .remove("model_$providerId")
            .remove("name_$providerId")
            .remove("chat_model_$providerId")
            .remove("chat_name_$providerId")
            .apply()
        removeProviderId(providerId)
        if (prefs.getString("default_provider", null) == providerId) {
            prefs.edit().remove("default_provider").apply()
            _currentProvider.value = null
        }
    }

    private fun loadCurrentProvider(): ProviderConfig? {
        val id = prefs.getString("default_provider", null) ?: return null
        return getProviderConfig(id)
    }

    private fun getSavedProviderIds(): Set<String> {
        return prefs.getStringSet("provider_ids", emptySet()) ?: emptySet()
    }

    private fun addProviderId(id: String) {
        val ids = getSavedProviderIds().toMutableSet()
        ids.add(id)
        prefs.edit().putStringSet("provider_ids", ids).apply()
    }

    private fun removeProviderId(id: String) {
        val ids = getSavedProviderIds().toMutableSet()
        ids.remove(id)
        prefs.edit().putStringSet("provider_ids", ids).apply()
    }
}
