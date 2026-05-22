package com.gzzz.toimage.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gzzz.toimage.data.local.ChatMessageEntity
import com.gzzz.toimage.data.local.ChatSessionEntity
import com.gzzz.toimage.data.network.ConnectivityObserver
import com.gzzz.toimage.data.provider.Capabilities
import com.gzzz.toimage.data.provider.ChatStreamEvent
import com.gzzz.toimage.data.provider.GenerationProgress
import com.gzzz.toimage.data.provider.GptImageProvider
import com.gzzz.toimage.data.provider.GrokProvider
import com.gzzz.toimage.data.provider.ImageProviderRegistry
import com.gzzz.toimage.data.repository.EnqueueError
import com.gzzz.toimage.data.repository.GenerationRepository
import com.gzzz.toimage.data.repository.HistoryRepository
import com.gzzz.toimage.data.repository.SettingsRepository
import com.gzzz.toimage.domain.model.ProviderConfig
import com.gzzz.toimage.ui.components.GenerationParams
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val isConnected: Boolean = true,
    val isDrawerOpen: Boolean = false,
    val currentSessionId: String? = null,
    val currentSession: ChatSessionEntity? = null,
    val isGenerating: Boolean = false,
    val isStreaming: Boolean = false,
    val streamingMessageId: String? = null,
    val params: GenerationParams = GenerationParams(),
    val currentProviderConfig: ProviderConfig? = null,
    val currentCapabilities: Capabilities = Capabilities(),
    val sourceImagePath: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    val settingsRepository: SettingsRepository,
    val connectivityObserver: ConnectivityObserver,
    private val generationRepository: GenerationRepository,
    private val historyRepository: HistoryRepository,
    private val providerRegistry: ImageProviderRegistry,
    private val gptImageProvider: GptImageProvider,
    private val grokProvider: GrokProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private var chatJob: Job? = null

    private val sessionId: String? = savedStateHandle["sessionId"]

    val messages: StateFlow<List<ChatMessageEntity>> = run {
        val id = sessionId
        if (id != null) {
            historyRepository.observeMessages(id)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        } else {
            MutableStateFlow(emptyList())
        }
    }

    val sessions: StateFlow<List<ChatSessionEntity>> =
        historyRepository.observeSessions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // 注册内置 provider
        providerRegistry.register(gptImageProvider)
        providerRegistry.register(grokProvider)

        // 配置所有已保存的 provider
        val allConfigs = settingsRepository.getAllProviderConfigs()
        for (config in allConfigs) {
            generationRepository.configureProvider(config)
        }

        // 设置当前 provider
        val config = settingsRepository.currentProvider.value
        if (config != null) {
            val provider = providerRegistry.get(config.id)
            _uiState.value = _uiState.value.copy(
                currentProviderConfig = config,
                currentCapabilities = provider?.capabilities ?: Capabilities()
            )
        }

        // 加载当前会话
        if (sessionId != null) {
            viewModelScope.launch {
                generationRepository.recoverRunningTextMessages(sessionId)
                val session = historyRepository.getSession(sessionId)
                _uiState.value = _uiState.value.copy(
                    currentSessionId = sessionId,
                    currentSession = session
                )
            }
        }

        // 监听当前 provider 配置变化
        viewModelScope.launch {
            settingsRepository.currentProvider.collectLatest { config ->
                if (config != null) {
                    generationRepository.configureProvider(config)
                    val provider = providerRegistry.get(config.id)
                    _uiState.value = _uiState.value.copy(
                        currentProviderConfig = config,
                        currentCapabilities = provider?.capabilities ?: Capabilities()
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        currentProviderConfig = null
                    )
                }
            }
        }

        // 监听网络状态
        viewModelScope.launch {
            connectivityObserver.observeConnection.collect { connected ->
                _uiState.value = _uiState.value.copy(isConnected = connected)
            }
        }

        // 监听生成事件
        viewModelScope.launch {
            generationRepository.events.collect { event ->
                when (event.progress) {
                    is GenerationProgress.Success, is GenerationProgress.Failed -> {
                        _uiState.value = _uiState.value.copy(isGenerating = false)
                    }
                    else -> {}
                }
            }
        }
    }

    fun sendMessage(prompt: String) {
        val config = _uiState.value.currentProviderConfig
        if (config == null) {
            viewModelScope.launch { _toastMessage.emit("请先在设置中配置 API Key") }
            return
        }
        if (prompt.isBlank()) return

        val provider = providerRegistry.get(config.id)
        if (provider == null || !provider.isConfigured) {
            viewModelScope.launch { _toastMessage.emit("当前服务未配置，请在设置中填写 API Key") }
            return
        }

        val hasSourceImage = _uiState.value.sourceImagePath != null
        val canChat = provider.capabilities.supportsTextChat && !hasSourceImage

        if (canChat) {
            sendChat(prompt)
        } else {
            enqueueGeneration(prompt)
        }
    }

    private fun sendChat(prompt: String) {
        val config = _uiState.value.currentProviderConfig ?: return

        chatJob = viewModelScope.launch {
            var currentSessionId = _uiState.value.currentSessionId

            if (currentSessionId == null) {
                val session = historyRepository.createSession(
                    providerId = config.id,
                    model = config.chat.model,
                    firstPrompt = prompt
                )
                currentSessionId = session.id
                _uiState.value = _uiState.value.copy(
                    currentSessionId = currentSessionId,
                    currentSession = session
                )
            }

            _uiState.value = _uiState.value.copy(isGenerating = true, isStreaming = true)

            generationRepository.sendChat(
                sessionId = currentSessionId,
                userText = prompt,
                providerId = config.id,
                model = config.chat.model,
                apiKey = config.chat.apiKey.ifBlank { config.image.apiKey }
            ).collect { event ->
                when (event) {
                    is ChatStreamEvent.Delta -> {}
                    is ChatStreamEvent.Done -> {
                        _uiState.value = _uiState.value.copy(
                            isGenerating = false,
                            isStreaming = false,
                            streamingMessageId = null
                        )
                    }
                    is ChatStreamEvent.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isGenerating = false,
                            isStreaming = false,
                            streamingMessageId = null
                        )
                        val msg = when (event.error) {
                            is com.gzzz.toimage.data.provider.GenerationError.NetworkUnavailable -> "网络不可用，请检查网络连接"
                            is com.gzzz.toimage.data.provider.GenerationError.ApiError -> "请求失败：${(event.error as com.gzzz.toimage.data.provider.GenerationError.ApiError).message}"
                            is com.gzzz.toimage.data.provider.GenerationError.Timeout -> "请求超时，请重试"
                            is com.gzzz.toimage.data.provider.GenerationError.ContentRejected -> "内容不合规，请修改"
                            else -> "对话失败：${event.error}"
                        }
                        _toastMessage.emit(msg)
                    }
                }
            }
        }
    }

    private fun enqueueGeneration(prompt: String) {
        val config = _uiState.value.currentProviderConfig ?: return

        viewModelScope.launch {
            var currentSessionId = _uiState.value.currentSessionId

            if (currentSessionId == null) {
                val session = historyRepository.createSession(
                    providerId = config.id,
                    model = config.image.model,
                    firstPrompt = prompt
                )
                currentSessionId = session.id
                _uiState.value = _uiState.value.copy(
                    currentSessionId = currentSessionId,
                    currentSession = session
                )
            }

            _uiState.value = _uiState.value.copy(isGenerating = true)

            val error = generationRepository.enqueue(
                sessionId = currentSessionId,
                prompt = prompt,
                size = _uiState.value.params.size,
                providerId = config.id,
                model = config.image.model,
                apiKey = config.image.apiKey,
                sourceImagePath = _uiState.value.sourceImagePath
            )

            if (error != null) {
                _uiState.value = _uiState.value.copy(isGenerating = false)
                val msg = when (error) {
                    EnqueueError.NO_API_KEY -> "请先在设置中配置 API Key"
                    EnqueueError.NO_NETWORK -> "网络不可用，请检查网络连接"
                    EnqueueError.PROVIDER_NOT_FOUND -> "未找到对应的图片生成服务"
                }
                _toastMessage.emit(msg)
            } else {
                _uiState.value = _uiState.value.copy(sourceImagePath = null)
            }
        }
    }

    fun cancelGeneration() {
        generationRepository.cancel()
        chatJob?.cancel()
        chatJob = null
        _uiState.value = _uiState.value.copy(isGenerating = false, isStreaming = false)
    }

    fun toggleDrawer() {
        _uiState.value = _uiState.value.copy(isDrawerOpen = !_uiState.value.isDrawerOpen)
    }

    fun closeDrawer() {
        _uiState.value = _uiState.value.copy(isDrawerOpen = false)
    }

    fun updateParams(params: GenerationParams) {
        _uiState.value = _uiState.value.copy(params = params)
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            historyRepository.deleteSession(sessionId)
            if (_uiState.value.currentSessionId == sessionId) {
                _uiState.value = _uiState.value.copy(
                    currentSessionId = null,
                    currentSession = null
                )
            }
        }
    }

    fun newSession() {
        _uiState.value = _uiState.value.copy(
            currentSessionId = null,
            currentSession = null
        )
    }

    suspend fun createNewSession(): String? {
        val config = _uiState.value.currentProviderConfig ?: return null
        val session = historyRepository.createSession(
            providerId = config.id,
            model = config.chat.model,
            firstPrompt = "新对话"
        )
        _uiState.value = _uiState.value.copy(
            currentSessionId = session.id,
            currentSession = session
        )
        return session.id
    }

    fun setSourceImage(path: String) {
        _uiState.value = _uiState.value.copy(sourceImagePath = path)
    }

    fun clearSourceImage() {
        _uiState.value = _uiState.value.copy(sourceImagePath = null)
    }
}
