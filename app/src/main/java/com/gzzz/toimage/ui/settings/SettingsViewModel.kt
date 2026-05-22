package com.gzzz.toimage.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gzzz.toimage.data.remote.ModelsApiService
import com.gzzz.toimage.data.repository.HistoryRepository
import com.gzzz.toimage.data.repository.SettingsRepository
import com.gzzz.toimage.data.storage.StorageCleaner
import com.gzzz.toimage.data.storage.StorageInfo
import com.gzzz.toimage.domain.model.ProviderConfig
import com.gzzz.toimage.domain.model.ServiceConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    private val storageCleaner: StorageCleaner
) : ViewModel() {

    private val _imageModels = MutableStateFlow<List<String>>(emptyList())
    val imageModels: StateFlow<List<String>> = _imageModels.asStateFlow()

    private val _chatModels = MutableStateFlow<List<String>>(emptyList())
    val chatModels: StateFlow<List<String>> = _chatModels.asStateFlow()

    private val _isLoadingImageModels = MutableStateFlow(false)
    val isLoadingImageModels: StateFlow<Boolean> = _isLoadingImageModels.asStateFlow()

    private val _isLoadingChatModels = MutableStateFlow(false)
    val isLoadingChatModels: StateFlow<Boolean> = _isLoadingChatModels.asStateFlow()

    fun getCurrentConfig(): ProviderConfig? = settingsRepository.currentProvider.value

    fun saveProvider(
        imageConfig: ServiceConfig,
        chatConfig: ServiceConfig
    ) {
        val config = ProviderConfig(
            id = "gpt-image-2",
            image = imageConfig,
            chat = chatConfig,
            isDefault = true
        )
        settingsRepository.saveProviderConfig(config)
        settingsRepository.setCurrentProvider(config.id)
    }

    fun fetchImageModels(baseUrl: String, apiKey: String) {
        if (baseUrl.isBlank() || apiKey.isBlank()) return
        viewModelScope.launch {
            _isLoadingImageModels.value = true
            val models = fetchModels(baseUrl, apiKey)
            _imageModels.value = models
            _isLoadingImageModels.value = false
        }
    }

    fun fetchChatModels(baseUrl: String, apiKey: String) {
        if (baseUrl.isBlank() || apiKey.isBlank()) return
        viewModelScope.launch {
            _isLoadingChatModels.value = true
            val models = fetchModels(baseUrl, apiKey)
            _chatModels.value = models
            _isLoadingChatModels.value = false
        }
    }

    private suspend fun fetchModels(baseUrl: String, apiKey: String): List<String> =
        withContext(Dispatchers.IO) {
            try {
                val json = Json { ignoreUnknownKeys = true }
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    })
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl.trimEnd('/') + "/")
                    .client(client)
                    .addConverterFactory("application/json".toMediaType().let { json.asConverterFactory(it) })
                    .build()

                val service = retrofit.create(ModelsApiService::class.java)
                val response = service.listModels("Bearer $apiKey")
                response.data.map { it.id }.sorted()
            } catch (e: Exception) {
                emptyList()
            }
        }

    suspend fun getStorageInfo(): StorageInfo = storageCleaner.getStorageInfo()

    fun clearAllHistory() {
        viewModelScope.launch {
            historyRepository.deleteAllSessions()
            storageCleaner.clearAll()
        }
    }

    fun clearAllImages() {
        viewModelScope.launch {
            storageCleaner.clearAll()
        }
    }
}
