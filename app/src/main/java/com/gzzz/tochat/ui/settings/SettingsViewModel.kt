package com.gzzz.tochat.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gzzz.tochat.data.local.ApiConfigEntity
import com.gzzz.tochat.data.local.KnowledgeDocumentEntity
import com.gzzz.tochat.data.provider.PROVIDER_GPT_IMAGE
import com.gzzz.tochat.data.remote.ModelsApiService
import com.gzzz.tochat.data.repository.HistoryRepository
import com.gzzz.tochat.data.repository.KnowledgeRepository
import com.gzzz.tochat.data.repository.SettingsRepository
import com.gzzz.tochat.data.storage.StorageCleaner
import com.gzzz.tochat.data.storage.StorageInfo
import com.gzzz.tochat.domain.model.ProviderConfig
import com.gzzz.tochat.domain.model.ServiceConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    private val storageCleaner: StorageCleaner,
    private val knowledgeRepository: KnowledgeRepository
) : ViewModel() {

    private val _imageModels = MutableStateFlow<List<String>>(emptyList())
    val imageModels: StateFlow<List<String>> = _imageModels.asStateFlow()

    private val _chatModels = MutableStateFlow<List<String>>(emptyList())
    val chatModels: StateFlow<List<String>> = _chatModels.asStateFlow()

    private val _isLoadingImageModels = MutableStateFlow(false)
    val isLoadingImageModels: StateFlow<Boolean> = _isLoadingImageModels.asStateFlow()

    private val _isLoadingChatModels = MutableStateFlow(false)
    val isLoadingChatModels: StateFlow<Boolean> = _isLoadingChatModels.asStateFlow()

    private val _apiConfigs = MutableStateFlow<List<ApiConfigEntity>>(emptyList())
    val apiConfigs: StateFlow<List<ApiConfigEntity>> = _apiConfigs.asStateFlow()

    private val _chatConfigs = MutableStateFlow<List<ApiConfigEntity>>(emptyList())
    val chatConfigs: StateFlow<List<ApiConfigEntity>> = _chatConfigs.asStateFlow()

    private val _imageConfigs = MutableStateFlow<List<ApiConfigEntity>>(emptyList())
    val imageConfigs: StateFlow<List<ApiConfigEntity>> = _imageConfigs.asStateFlow()

    private val _isImportingKnowledge = MutableStateFlow(false)
    val isImportingKnowledge: StateFlow<Boolean> = _isImportingKnowledge.asStateFlow()

    val knowledgeDocuments: StateFlow<List<KnowledgeDocumentEntity>> =
        knowledgeRepository.observeDocuments()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val backgroundPath: StateFlow<String?> = settingsRepository.backgroundPath

    init {
        viewModelScope.launch {
            settingsRepository.observeApiConfigs().collect { configs ->
                _apiConfigs.value = configs
            }
        }
        viewModelScope.launch {
            settingsRepository.observeApiConfigsByType("chat").collect { configs ->
                _chatConfigs.value = configs
            }
        }
        viewModelScope.launch {
            settingsRepository.observeApiConfigsByType("image").collect { configs ->
                _imageConfigs.value = configs
            }
        }
    }

    fun getCurrentConfig(): ProviderConfig? = settingsRepository.currentProvider.value

    fun setBackgroundPath(path: String?) {
        settingsRepository.setBackgroundPath(path)
    }

    fun clearBackgroundPath() {
        settingsRepository.setBackgroundPath(null)
    }

    fun saveProvider(
        imageConfig: ServiceConfig
    ) {
        val config = ProviderConfig(
            id = PROVIDER_GPT_IMAGE,
            image = imageConfig,
            chat = ServiceConfig(displayName = "", baseUrl = "", apiKey = "", model = ""),
            isDefault = true
        )
        settingsRepository.saveProviderConfig(config)
        settingsRepository.setCurrentProvider(config.id)
    }

    fun fetchImageModels(baseUrl: String, apiKey: String) {
        if (baseUrl.isBlank() || apiKey.isBlank()) return
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) return
        viewModelScope.launch {
            _isLoadingImageModels.value = true
            val models = fetchModels(baseUrl, apiKey)
            _imageModels.value = models
            _isLoadingImageModels.value = false
        }
    }

    fun fetchChatModels(baseUrl: String, apiKey: String) {
        if (baseUrl.isBlank() || apiKey.isBlank()) return
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) return
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
                // 校验 URL 格式
                if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
                    return@withContext emptyList()
                }

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

    fun importKnowledgeDocument(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            _isImportingKnowledge.value = true
            val result = knowledgeRepository.importDocument(uri)
            _isImportingKnowledge.value = false
            result.fold(
                onSuccess = { document ->
                    val message = if (document.status == "ready") {
                        "已导入：${document.fileName}"
                    } else {
                        "该文件已在知识库中"
                    }
                    onResult(message)
                },
                onFailure = { e -> onResult(e.message ?: "导入失败") }
            )
        }
    }

    fun deleteKnowledgeDocument(id: String) {
        viewModelScope.launch {
            knowledgeRepository.deleteDocument(id)
            settingsRepository.removeKnowledgeDocumentSelection(id)
        }
    }

    // ===== 多配置管理 =====

    suspend fun getApiConfigs(): List<ApiConfigEntity> {
        return settingsRepository.getApiConfigs()
    }

    fun saveApiConfig(
        id: String = UUID.randomUUID().toString(),
        name: String,
        baseUrl: String,
        apiKey: String,
        models: List<String>,
        type: String = "chat",
        providerId: String? = null
    ) {
        viewModelScope.launch {
            val config = ApiConfigEntity(
                id = id,
                name = name,
                baseUrl = baseUrl.trimEnd('/'),
                apiKey = apiKey,
                models = Json.encodeToString(models),
                type = type,
                providerId = providerId
            )
            settingsRepository.saveApiConfig(config)
        }
    }

    fun deleteApiConfig(id: String) {
        viewModelScope.launch {
            settingsRepository.deleteApiConfig(id)
        }
    }

    fun fetchModelsForConfig(baseUrl: String, apiKey: String, onResult: (List<String>) -> Unit) {
        if (baseUrl.isBlank() || apiKey.isBlank()) return
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) return
        viewModelScope.launch {
            val models = fetchModels(baseUrl, apiKey)
            onResult(models)
        }
    }
}
