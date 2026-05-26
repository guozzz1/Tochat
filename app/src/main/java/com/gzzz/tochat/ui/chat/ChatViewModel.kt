package com.gzzz.tochat.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gzzz.tochat.data.local.ApiConfigEntity
import com.gzzz.tochat.data.local.ChatMessageEntity
import com.gzzz.tochat.data.local.ChatSessionEntity
import com.gzzz.tochat.data.local.KnowledgeDocumentEntity
import com.gzzz.tochat.data.network.ConnectivityObserver
import com.gzzz.tochat.data.provider.Capabilities
import com.gzzz.tochat.data.provider.ChatStreamEvent
import com.gzzz.tochat.data.provider.GenerationProgress
import com.gzzz.tochat.data.provider.GptImageProvider
import com.gzzz.tochat.data.provider.GrokProvider
import com.gzzz.tochat.data.provider.ImageProviderRegistry
import com.gzzz.tochat.data.provider.PROVIDER_GPT_IMAGE
import com.gzzz.tochat.data.provider.effectiveImageProviderId
import com.gzzz.tochat.data.repository.EnqueueError
import com.gzzz.tochat.data.repository.GenerationRepository
import com.gzzz.tochat.data.repository.HistoryRepository
import com.gzzz.tochat.data.repository.KnowledgeRepository
import com.gzzz.tochat.data.repository.KnowledgeSnippet
import com.gzzz.tochat.data.repository.RoundtableParticipant
import com.gzzz.tochat.data.repository.RoundtableRunEvent
import com.gzzz.tochat.data.repository.SettingsRepository
import com.gzzz.tochat.domain.model.ProviderConfig
import com.gzzz.tochat.domain.model.ServiceConfig
import com.gzzz.tochat.ui.components.GenerationParams
import com.gzzz.tochat.util.FileAttachment
import com.gzzz.tochat.util.FileAttachmentFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class PromptContext(
    val prompt: String,
    val snippetCount: Int
)

data class ChatUiState(
    val isConnected: Boolean = true,
    val currentSessionId: String? = null,
    val currentSession: ChatSessionEntity? = null,
    val isGenerating: Boolean = false,
    val isStreaming: Boolean = false,
    val streamingMessageId: String? = null,
    val params: GenerationParams = GenerationParams(),
    val currentProviderConfig: ProviderConfig? = null,
    val currentCapabilities: Capabilities = Capabilities(),
    val sourceImagePath: String? = null,
    val currentChatConfigId: String? = null,
    val currentChatModel: String? = null,
    val currentImageConfigId: String? = null,
    val currentImageModel: String? = null,
    val fileAttachment: FileAttachment? = null,
    val isImageMode: Boolean = false,
    val imageHintVisible: Boolean = false,
    val imageHintPrompt: String? = null,
    val isRoundtableRunning: Boolean = false,
    val roundtableStatusLabel: String? = null,
    val knowledgeEnabled: Boolean = false,
    val selectedKnowledgeDocumentIds: Set<String> = emptySet(),
    val lastKnowledgeSnippetCount: Int = 0
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    val settingsRepository: SettingsRepository,
    val connectivityObserver: ConnectivityObserver,
    private val generationRepository: GenerationRepository,
    private val historyRepository: HistoryRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val providerRegistry: ImageProviderRegistry,
    private val gptImageProvider: GptImageProvider,
    private val grokProvider: GrokProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val backgroundPath: StateFlow<String?> = settingsRepository.backgroundPath

    val knowledgeDocuments: StateFlow<List<KnowledgeDocumentEntity>> =
        knowledgeRepository.observeDocuments()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private var chatJob: Job? = null
    private var roundtableJob: Job? = null

    private val sessionId: String? = savedStateHandle["sessionId"]
    private val _currentSessionId = MutableStateFlow(sessionId)

    val messages: StateFlow<List<ChatMessageEntity>> = _currentSessionId
        .flatMapLatest { id ->
            if (id != null) {
                historyRepository.observeMessages(id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessions: StateFlow<List<ChatSessionEntity>> =
        historyRepository.observeSessions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // 注册内置 provider
        providerRegistry.register(gptImageProvider)
        providerRegistry.register(grokProvider)

        // 配置所有已保存的 provider（用于生图）
        val allConfigs = settingsRepository.getAllProviderConfigs()
        for (config in allConfigs) {
            generationRepository.configureProvider(config)
        }

        // 设置当前 provider（生图使用）
        val config = settingsRepository.currentProvider.value
        if (config != null) {
            val provider = providerRegistry.get(config.id)
            _uiState.value = _uiState.value.copy(
                currentProviderConfig = config,
                currentCapabilities = provider?.capabilities ?: Capabilities()
            )
        }

        // 加载（或自动选择）当前对话模型
        viewModelScope.launch {
            val (savedConfigId, savedModel) = settingsRepository.getCurrentModel()
            val apiConfigs = settingsRepository.getApiConfigs()

            val (effectiveConfigId, effectiveModel) = pickActiveChatModel(savedConfigId, savedModel, apiConfigs)

            if (effectiveConfigId != null && effectiveModel != null) {
                if (effectiveConfigId != savedConfigId || effectiveModel != savedModel) {
                    settingsRepository.saveCurrentModel(effectiveConfigId, effectiveModel)
                }
                applyChatConfigById(effectiveConfigId, effectiveModel)
            }

            _uiState.value = _uiState.value.copy(
                currentChatConfigId = effectiveConfigId,
                currentChatModel = effectiveModel
            )
        }

        // 加载（或自动选择）当前生图模型
        viewModelScope.launch {
            val (savedConfigId, savedModel) = settingsRepository.getCurrentImageModel()
            val imageConfigs = settingsRepository.getApiConfigsByType("image")

            val (effectiveConfigId, effectiveModel) = pickActiveChatModel(savedConfigId, savedModel, imageConfigs)

            if (effectiveConfigId != null && effectiveModel != null) {
                if (effectiveConfigId != savedConfigId || effectiveModel != savedModel) {
                    settingsRepository.saveCurrentImageModel(effectiveConfigId, effectiveModel)
                }
                applyImageConfigById(effectiveConfigId, effectiveModel)
            }

            _uiState.value = _uiState.value.copy(
                currentImageConfigId = effectiveConfigId,
                currentImageModel = effectiveModel
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

        viewModelScope.launch {
            settingsRepository.knowledgeEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(knowledgeEnabled = enabled)
            }
        }

        viewModelScope.launch {
            settingsRepository.selectedKnowledgeDocumentIds.collect { ids ->
                _uiState.value = _uiState.value.copy(selectedKnowledgeDocumentIds = ids)
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
        if (prompt.isBlank()) return

        dismissImageHint()

        val hasFile = _uiState.value.fileAttachment != null
        val hasSourceImage = _uiState.value.sourceImagePath != null

        val isImageRequest = when {
            hasFile -> false
            _uiState.value.isImageMode -> true
            hasSourceImage -> true
            else -> false
        }

        if (isImageRequest) {
            enqueueGeneration(prompt)
            if (_uiState.value.isImageMode) exitImageMode()
        } else {
            if (!hasFile && detectImageIntent(prompt)) {
                _uiState.value = _uiState.value.copy(
                    imageHintVisible = true,
                    imageHintPrompt = prompt
                )
            } else {
                sendChat(prompt)
            }
        }
    }

    fun confirmImageHint() {
        val prompt = _uiState.value.imageHintPrompt ?: return
        dismissImageHint()
        enqueueGeneration(prompt)
    }

    fun dismissImageHintAndSendChat() {
        val prompt = _uiState.value.imageHintPrompt ?: return
        dismissImageHint()
        sendChat(prompt)
    }

    fun dismissImageHint() {
        _uiState.value = _uiState.value.copy(
            imageHintVisible = false,
            imageHintPrompt = null
        )
    }

    fun enterImageMode() {
        _uiState.value = _uiState.value.copy(isImageMode = true)
    }

    fun exitImageMode() {
        _uiState.value = _uiState.value.copy(isImageMode = false)
    }

    fun setKnowledgeEnabled(enabled: Boolean) {
        settingsRepository.setKnowledgeEnabled(enabled)
    }

    fun toggleKnowledgeDocument(id: String) {
        val ids = _uiState.value.selectedKnowledgeDocumentIds.toMutableSet()
        if (!ids.add(id)) ids.remove(id)
        settingsRepository.setSelectedKnowledgeDocumentIds(ids)
    }

    fun clearKnowledgeSelection() {
        settingsRepository.setSelectedKnowledgeDocumentIds(emptySet())
    }

    private fun detectImageIntent(prompt: String): Boolean {
        val blacklist = listOf(
            "画法", "画风", "画作", "画家", "画笔", "画布", "画面",
            "怎么画", "如何画", "画.*的方法", "画.*的技巧",
            "描述", "解释", "介绍", "分析", "总结",
            "生成.*表格", "生成.*代码", "生成.*文档", "生成.*脚本",
            "生成.*列表", "生成.*报告", "生成.*方案", "生成.*计划"
        )
        for (pattern in blacklist) {
            if (Regex(pattern).containsMatchIn(prompt)) return false
        }

        val whitelist = listOf(
            "画一[只个张幅副]",
            "画[一]?.*的(图|画像|海报|插画|头像|壁纸|照片)",
            "生成.*图(片|像)?$",
            "生成一[张幅副]",
            "帮我画",
            "帮我生成.*图",
            "出[一张幅].*图",
            "^画.{1,8}$"
        )
        for (pattern in whitelist) {
            if (Regex(pattern).containsMatchIn(prompt)) return true
        }

        return false
    }

    private fun sendChat(prompt: String) {
        chatJob = viewModelScope.launch {
            val configId = _uiState.value.currentChatConfigId
            val model = _uiState.value.currentChatModel

            if (configId == null || model == null) {
                _toastMessage.emit("请先在设置中添加配置并选择模型")
                return@launch
            }

            val apiConfig = settingsRepository.getApiConfigById(configId)
            if (apiConfig == null) {
                _toastMessage.emit("当前配置已被删除，请重新选择模型")
                return@launch
            }

            val attachment = _uiState.value.fileAttachment
            val attachmentFileName = attachment?.fileName
            val promptContext = buildFinalUserPrompt(prompt, attachment)
            val finalPrompt = promptContext.prompt
            _uiState.value = _uiState.value.copy(
                fileAttachment = null,
                lastKnowledgeSnippetCount = promptContext.snippetCount
            )

            // 应用对话配置到 provider
            gptImageProvider.configureChat(apiConfig.baseUrl, apiConfig.apiKey, model)

            var currentSessionId = _uiState.value.currentSessionId

            if (currentSessionId == null) {
                val session = historyRepository.createSession(
                    providerId = PROVIDER_GPT_IMAGE,
                    model = model,
                    firstPrompt = prompt
                )
                currentSessionId = session.id
                _currentSessionId.value = currentSessionId
                _uiState.value = _uiState.value.copy(
                    currentSessionId = currentSessionId,
                    currentSession = session
                )
            }

            _uiState.value = _uiState.value.copy(isGenerating = true, isStreaming = true)

            generationRepository.sendChat(
                sessionId = currentSessionId,
                userText = finalPrompt,
                displayText = prompt,
                attachmentFileName = attachmentFileName,
                providerId = PROVIDER_GPT_IMAGE,
                model = model,
                apiKey = apiConfig.apiKey
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
                            is com.gzzz.tochat.data.provider.GenerationError.NetworkUnavailable -> "网络不可用，请检查网络连接"
                            is com.gzzz.tochat.data.provider.GenerationError.ApiError -> {
                                val error = event.error as com.gzzz.tochat.data.provider.GenerationError.ApiError
                                when (error.code) {
                                    503 -> "服务暂时不可用，请稍后重试"
                                    429 -> "请求过于频繁，请稍后重试"
                                    401, 403 -> "API Key 无效，请检查配置"
                                    else -> "请求失败：${error.message}"
                                }
                            }
                            is com.gzzz.tochat.data.provider.GenerationError.Timeout -> "请求超时，请重试"
                            is com.gzzz.tochat.data.provider.GenerationError.ContentRejected -> "内容不合规，请修改"
                            else -> "对话失败：${event.error}"
                        }
                        _toastMessage.emit(msg)
                    }
                }
            }
        }
    }

    private fun enqueueGeneration(prompt: String) {
        val configId = _uiState.value.currentImageConfigId
        val model = _uiState.value.currentImageModel

        if (configId == null || model == null) {
            viewModelScope.launch { _toastMessage.emit("请先选择生图模型") }
            return
        }

        viewModelScope.launch {
            val apiConfig = settingsRepository.getApiConfigById(configId)
            if (apiConfig == null) {
                _toastMessage.emit("当前生图配置已被删除，请重新选择")
                return@launch
            }

            val providerId = apiConfig.effectiveImageProviderId()
            applyImageConfig(apiConfig, model)

            var currentSessionId = _uiState.value.currentSessionId

            if (currentSessionId == null) {
                val session = historyRepository.createSession(
                    providerId = providerId,
                    model = model,
                    firstPrompt = prompt
                )
                currentSessionId = session.id
                _currentSessionId.value = currentSessionId
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
                providerId = providerId,
                model = model,
                apiKey = apiConfig.apiKey,
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

    fun startRoundtable(prompt: String, participants: List<RoundtableParticipant>, maxRounds: Int) {
        if (prompt.isBlank()) return
        if (_uiState.value.isImageMode || _uiState.value.sourceImagePath != null) {
            viewModelScope.launch { _toastMessage.emit("圆桌讨论暂只支持文本，请先退出生图模式") }
            return
        }
        if (participants.size !in 2..4) {
            viewModelScope.launch { _toastMessage.emit("请选择 2-4 个对话模型") }
            return
        }

        roundtableJob = viewModelScope.launch {
            val attachment = _uiState.value.fileAttachment
            val attachmentFileName = attachment?.fileName
            val promptContext = buildFinalUserPrompt(prompt, attachment)
            val finalPrompt = promptContext.prompt
            _uiState.value = _uiState.value.copy(
                fileAttachment = null,
                lastKnowledgeSnippetCount = promptContext.snippetCount
            )

            var currentSessionId = _uiState.value.currentSessionId
            if (currentSessionId == null) {
                val session = historyRepository.createSession(
                    providerId = "roundtable",
                    model = participants.joinToString(",") { it.model },
                    firstPrompt = prompt
                )
                currentSessionId = session.id
                _currentSessionId.value = currentSessionId
                _uiState.value = _uiState.value.copy(
                    currentSessionId = currentSessionId,
                    currentSession = session
                )
            }

            _uiState.value = _uiState.value.copy(
                isGenerating = true,
                isStreaming = true,
                isRoundtableRunning = true,
                roundtableStatusLabel = "圆桌讨论开始"
            )

            try {
                generationRepository.sendRoundtable(
                    sessionId = currentSessionId,
                    userText = finalPrompt,
                    displayText = prompt,
                    attachmentFileName = attachmentFileName,
                    participants = participants,
                    maxRounds = maxRounds
                ).collect { event ->
                    when (event) {
                        RoundtableRunEvent.Started -> {
                            _uiState.value = _uiState.value.copy(roundtableStatusLabel = "圆桌讨论开始")
                        }
                        is RoundtableRunEvent.TurnStarted -> {
                            _uiState.value = _uiState.value.copy(
                                roundtableStatusLabel = "第 ${event.round} 轮：${event.participant.configName} 思考中"
                            )
                        }
                        is RoundtableRunEvent.TurnCompleted -> {
                            _uiState.value = _uiState.value.copy(
                                roundtableStatusLabel = "第 ${event.round} 轮：${event.participant.configName} 已完成"
                            )
                        }
                        is RoundtableRunEvent.SummaryStarted -> {
                            _uiState.value = _uiState.value.copy(roundtableStatusLabel = "Leader 正在总结")
                        }
                        RoundtableRunEvent.Completed -> {
                            _uiState.value = _uiState.value.copy(roundtableStatusLabel = "圆桌讨论完成")
                        }
                        is RoundtableRunEvent.Error -> {
                            _toastMessage.emit(event.message)
                        }
                    }
                }
            } catch (e: CancellationException) {
                _toastMessage.emit("圆桌讨论已停止")
            } finally {
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    isStreaming = false,
                    isRoundtableRunning = false,
                    roundtableStatusLabel = null
                )
                roundtableJob = null
            }
        }
    }

    fun cancelGeneration() {
        generationRepository.cancel()
        chatJob?.cancel()
        chatJob = null
        roundtableJob?.cancel()
        roundtableJob = null
        _uiState.value = _uiState.value.copy(
            isGenerating = false,
            isStreaming = false,
            isRoundtableRunning = false,
            roundtableStatusLabel = null
        )
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

    fun renameSession(sessionId: String, newTitle: String) {
        val title = newTitle.trim()
        viewModelScope.launch {
            if (title.isBlank()) {
                _toastMessage.emit("标题不能为空")
                return@launch
            }

            val renamed = historyRepository.renameSession(sessionId, title)
            if (renamed == null) {
                _toastMessage.emit("会话不存在")
                return@launch
            }

            if (_uiState.value.currentSessionId == sessionId) {
                _uiState.value = _uiState.value.copy(currentSession = renamed)
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
        val model = _uiState.value.currentChatModel ?: "chat"
        val session = historyRepository.createSession(
            providerId = PROVIDER_GPT_IMAGE,
            model = model,
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

    fun setFileAttachment(attachment: FileAttachment) {
        _uiState.value = _uiState.value.copy(fileAttachment = attachment)
    }

    fun clearFileAttachment() {
        _uiState.value = _uiState.value.copy(fileAttachment = null)
    }

    fun switchChatModel(configId: String, model: String) {
        settingsRepository.saveCurrentModel(configId, model)
        _uiState.value = _uiState.value.copy(
            currentChatConfigId = configId,
            currentChatModel = model
        )
        viewModelScope.launch {
            applyChatConfigById(configId, model)
        }
    }

    fun switchImageModel(configId: String, model: String) {
        settingsRepository.saveCurrentImageModel(configId, model)
        _uiState.value = _uiState.value.copy(
            currentImageConfigId = configId,
            currentImageModel = model
        )
        viewModelScope.launch {
            applyImageConfigById(configId, model)
        }
    }

    suspend fun getAllApiConfigs(): List<com.gzzz.tochat.data.local.ApiConfigEntity> {
        return settingsRepository.getApiConfigs()
    }

    suspend fun getChatApiConfigs(): List<com.gzzz.tochat.data.local.ApiConfigEntity> {
        return settingsRepository.getApiConfigsByType("chat")
    }

    suspend fun getImageApiConfigs(): List<com.gzzz.tochat.data.local.ApiConfigEntity> {
        return settingsRepository.getApiConfigsByType("image")
    }

    private suspend fun applyChatConfigById(configId: String, model: String) {
        val apiConfig = settingsRepository.getApiConfigById(configId) ?: return
        gptImageProvider.configureChat(apiConfig.baseUrl, apiConfig.apiKey, model)
    }

    private suspend fun buildFinalUserPrompt(prompt: String, attachment: FileAttachment?): PromptContext {
        val basePrompt = if (attachment != null) {
            FileAttachmentFormatter.toModelPrompt(attachment, prompt)
        } else {
            prompt
        }

        val state = _uiState.value
        if (!state.knowledgeEnabled || state.selectedKnowledgeDocumentIds.isEmpty()) {
            return PromptContext(basePrompt, 0)
        }

        val snippets = try {
            knowledgeRepository.retrieve(prompt, state.selectedKnowledgeDocumentIds)
        } catch (e: Exception) {
            _toastMessage.emit("知识库检索失败，已按普通对话发送")
            emptyList()
        }
        if (snippets.isEmpty()) return PromptContext(basePrompt, 0)

        return PromptContext(
            prompt = buildKnowledgePrompt(prompt, attachment, snippets),
            snippetCount = snippets.size
        )
    }

    private fun buildKnowledgePrompt(
        prompt: String,
        attachment: FileAttachment?,
        snippets: List<KnowledgeSnippet>
    ): String {
        val knowledgeContext = buildString {
            appendLine("你正在回答用户问题。以下是从本地知识库检索到的相关片段，片段可能不完整。")
            appendLine("请优先基于这些片段回答；如果片段不足以支持结论，请明确说明“知识库中没有足够信息”，不要编造。")
            appendLine()
            appendLine("<本地知识库>")
            snippets.forEachIndexed { index, snippet ->
                appendLine("[知识库${index + 1}]")
                appendLine("文档：${snippet.documentTitle}")
                appendLine("片段：第 ${snippet.chunkIndex + 1} 段")
                appendLine("内容：")
                appendLine(snippet.text)
                appendLine()
            }
            appendLine("</本地知识库>")
        }

        return if (attachment != null) {
            val truncNote = if (attachment.isTruncated) {
                "（注：文件过长，已截取前${attachment.text.length}字）\n"
            } else {
                ""
            }
            "[附件: ${attachment.fileName}]\n$truncNote${attachment.text}\n\n$knowledgeContext\n用户问题：\n$prompt"
        } else {
            "$knowledgeContext\n用户问题：\n$prompt"
        }
    }

    private suspend fun applyImageConfigById(configId: String, model: String) {
        val apiConfig = settingsRepository.getApiConfigById(configId) ?: return
        applyImageConfig(apiConfig, model)
    }

    private fun applyImageConfig(apiConfig: ApiConfigEntity, model: String) {
        val providerId = apiConfig.effectiveImageProviderId()
        val provider = providerRegistry.get(providerId) ?: return
        provider.configure(
            image = ServiceConfig(
                displayName = apiConfig.name,
                baseUrl = apiConfig.baseUrl,
                apiKey = apiConfig.apiKey,
                model = model
            ),
            chat = ServiceConfig()
        )
        _uiState.value = _uiState.value.copy(currentCapabilities = provider.capabilities)
    }

    private fun pickActiveChatModel(
        savedConfigId: String?,
        savedModel: String?,
        apiConfigs: List<com.gzzz.tochat.data.local.ApiConfigEntity>
    ): Pair<String?, String?> {
        if (savedConfigId != null && savedModel != null) {
            val exists = apiConfigs.any { it.id == savedConfigId }
            if (exists) return savedConfigId to savedModel
        }
        val firstConfig = apiConfigs.firstOrNull() ?: return null to null
        val models = try {
            kotlinx.serialization.json.Json.decodeFromString<List<String>>(firstConfig.models)
        } catch (e: Exception) {
            emptyList()
        }
        val firstModel = models.firstOrNull() ?: return null to null
        return firstConfig.id to firstModel
    }
}
