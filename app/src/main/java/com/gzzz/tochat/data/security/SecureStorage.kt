package com.gzzz.tochat.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorage(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "tochat_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKey(providerId: String, apiKey: String) {
        prefs.edit().putString("api_key_$providerId", apiKey).apply()
    }

    fun getApiKey(providerId: String): String? {
        return prefs.getString("api_key_$providerId", null)
    }

    fun removeApiKey(providerId: String) {
        prefs.edit().remove("api_key_$providerId").apply()
    }

    fun saveBaseUrl(providerId: String, baseUrl: String) {
        prefs.edit().putString("base_url_$providerId", baseUrl).apply()
    }

    fun getBaseUrl(providerId: String): String? {
        return prefs.getString("base_url_$providerId", null)
    }

    fun removeBaseUrl(providerId: String) {
        prefs.edit().remove("base_url_$providerId").apply()
    }

    fun saveChatApiKey(providerId: String, chatApiKey: String) {
        prefs.edit().putString("chat_api_key_$providerId", chatApiKey).apply()
    }

    fun getChatApiKey(providerId: String): String? {
        return prefs.getString("chat_api_key_$providerId", null)
    }

    fun removeChatApiKey(providerId: String) {
        prefs.edit().remove("chat_api_key_$providerId").apply()
    }

    fun saveChatBaseUrl(providerId: String, chatBaseUrl: String) {
        prefs.edit().putString("chat_base_url_$providerId", chatBaseUrl).apply()
    }

    fun getChatBaseUrl(providerId: String): String? {
        return prefs.getString("chat_base_url_$providerId", null)
    }

    fun removeChatBaseUrl(providerId: String) {
        prefs.edit().remove("chat_base_url_$providerId").apply()
    }
}
