package com.gzzz.tochat.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.gzzz.tochat.data.local.ApiConfigEntity
import com.gzzz.tochat.data.local.ChatMessageEntity
import com.gzzz.tochat.data.local.KnowledgeDocumentEntity
import com.gzzz.tochat.data.provider.imageProviderDisplayName
import com.gzzz.tochat.data.repository.RoundtableParticipant
import com.gzzz.tochat.ui.components.AssistantMessageBubble
import com.gzzz.tochat.ui.components.ErrorMessageBubble
import com.gzzz.tochat.ui.components.LoadingMessageBubble
import com.gzzz.tochat.ui.components.ParamsBottomSheet
import com.gzzz.tochat.ui.components.TextMessageBubble
import com.gzzz.tochat.ui.components.UserMessageBubble
import com.gzzz.tochat.ui.session.SessionDrawerContent
import com.gzzz.tochat.ui.template.TemplateSheet
import com.gzzz.tochat.util.AiReplyShareImage
import com.gzzz.tochat.util.FileTextExtractor
import com.gzzz.tochat.util.ImagePickerUtil
import com.gzzz.tochat.util.RecentPhotosLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToSession: (String) -> Unit,
    onNavigateToImageDetail: (String) -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
    hasBackground: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val sessionSearchText by viewModel.sessionSearchText.collectAsState()
    val knowledgeDocuments by viewModel.knowledgeDocuments.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var showParamsSheet by remember { mutableStateOf(false) }
    var showTemplateSheet by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showModelSwitchSheet by remember { mutableStateOf(false) }
    var showImageModelSwitchSheet by remember { mutableStateOf(false) }
    var showRoundtableSheet by remember { mutableStateOf(false) }
    var showKnowledgeSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // 拍照临时文件路径
    var cameraPhotoPath by remember { mutableStateOf<String?>(null) }

    // 相册选择
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = ImagePickerUtil.processPickedImage(context, uri) { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
            if (path != null) {
                viewModel.setSourceImage(path)
            }
        }
    }

    // 拍照
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && cameraPhotoPath != null) {
            val path = ImagePickerUtil.processCapturedImage(context, cameraPhotoPath!!) { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
            if (path != null) {
                viewModel.setSourceImage(path)
            }
        }
        cameraPhotoPath = null
    }

    // 文件选择
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val result = FileTextExtractor.extract(context, uri)
            result.onSuccess { attachment ->
                viewModel.setFileAttachment(attachment)
                if (attachment.isTruncated) {
                    Toast.makeText(context, "文件过长，已截取前${attachment.text.length}字", Toast.LENGTH_SHORT).show()
                }
            }.onFailure { e ->
                Toast.makeText(context, e.message ?: "文件读取失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 附件选择弹窗
    if (showAttachmentSheet) {
        AttachmentSheet(
            onCameraClick = {
                showAttachmentSheet = false
                try {
                    val dir = File(context.filesDir, "input_images")
                    if (!dir.exists()) dir.mkdirs()
                    val photoFile = File(dir, "capture_${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        photoFile
                    )
                    cameraPhotoPath = photoFile.absolutePath
                    cameraLauncher.launch(uri)
                } catch (e: Exception) {
                    Toast.makeText(context, "无法启动相机：${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            },
            onGalleryClick = {
                showAttachmentSheet = false
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onFileClick = {
                showAttachmentSheet = false
                fileLauncher.launch(arrayOf(
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "text/plain",
                    "text/markdown",
                    "text/x-markdown"
                ))
            },
            onImageModeClick = {
                showAttachmentSheet = false
                viewModel.enterImageMode()
            },
            onPhotoSelect = { uri ->
                showAttachmentSheet = false
                val path = ImagePickerUtil.processPickedImage(context, uri) { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
                if (path != null) {
                    viewModel.setSourceImage(path)
                }
            },
            onDismiss = { showAttachmentSheet = false }
        )
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // 参数面板 — 使用 capabilities 动态渲染
    if (showParamsSheet) {
        ParamsBottomSheet(
            capabilities = uiState.currentCapabilities,
            currentParams = uiState.params,
            onDismiss = { showParamsSheet = false },
            onConfirm = {
                viewModel.updateParams(it)
                showParamsSheet = false
            }
        )
    }

    if (showTemplateSheet) {
        TemplateSheet(
            onDismiss = { showTemplateSheet = false },
            onSelect = { template ->
                inputText = template.prompt
                showTemplateSheet = false
            }
        )
    }

    if (showModelSwitchSheet) {
        ModelSwitchSheet(
            currentConfigId = uiState.currentChatConfigId,
            currentModel = uiState.currentChatModel,
            onDismiss = { showModelSwitchSheet = false },
            onModelSelect = { configId, model ->
                viewModel.switchChatModel(configId, model)
                showModelSwitchSheet = false
            },
            viewModel = viewModel
        )
    }

    if (showImageModelSwitchSheet) {
        ImageModelSwitchSheet(
            currentConfigId = uiState.currentImageConfigId,
            currentModel = uiState.currentImageModel,
            onDismiss = { showImageModelSwitchSheet = false },
            onModelSelect = { configId, model ->
                viewModel.switchImageModel(configId, model)
                showImageModelSwitchSheet = false
            },
            onParamsClick = {
                showImageModelSwitchSheet = false
                showParamsSheet = true
            },
            onTemplateClick = {
                showImageModelSwitchSheet = false
                showTemplateSheet = true
            },
            viewModel = viewModel
        )
    }

    if (showRoundtableSheet) {
        RoundtableSheet(
            prompt = inputText.trim(),
            onDismiss = { showRoundtableSheet = false },
            onStart = { participants, maxRounds ->
                viewModel.startRoundtable(inputText.trim(), participants, maxRounds)
                inputText = ""
                showRoundtableSheet = false
            },
            viewModel = viewModel
        )
    }

    if (showKnowledgeSheet) {
        KnowledgeSelectorSheet(
            documents = knowledgeDocuments,
            enabled = uiState.knowledgeEnabled,
            selectedIds = uiState.selectedKnowledgeDocumentIds,
            onEnabledChange = viewModel::setKnowledgeEnabled,
            onToggleDocument = viewModel::toggleKnowledgeDocument,
            onClearSelection = viewModel::clearKnowledgeSelection,
            onDismiss = { showKnowledgeSheet = false }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                SessionDrawerContent(
                    sessions = sessions,
                    sessionSearchText = sessionSearchText,
                    currentSessionId = uiState.currentSessionId,
                    onSessionClick = { id ->
                        scope.launch {
                            drawerState.close()
                            onNavigateToSession(id)
                        }
                    },
                    onNewSession = {
                        scope.launch {
                            drawerState.close()
                            val newSessionId = viewModel.createNewSession()
                            if (newSessionId != null) {
                                onNavigateToSession(newSessionId)
                            } else {
                                onNavigateToSession("")
                            }
                        }
                    },
                    onDeleteSession = { viewModel.deleteSession(it) },
                    onRenameSession = { id, title -> viewModel.renameSession(id, title) },
                    onSettingsClick = {
                        scope.launch {
                            drawerState.close()
                            onNavigateToSettings()
                        }
                    }
                )
            }
        },
        gesturesEnabled = true
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = uiState.currentSession?.title ?: "ToChat",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "菜单")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (hasBackground)
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        else MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (hasBackground)
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                            else MaterialTheme.colorScheme.surface
                        )
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    val roundtableStatusLabel = uiState.roundtableStatusLabel
                    if (roundtableStatusLabel != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = roundtableStatusLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // 源图片预览
                    if (uiState.sourceImagePath != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(
                                    model = File(uiState.sourceImagePath!!)
                                ),
                                contentDescription = "参考图",
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "图生图模式",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = { viewModel.clearSourceImage() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "移除图片",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 文件附件预览
                    if (uiState.fileAttachment != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = uiState.fileAttachment!!.fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (uiState.fileAttachment!!.isTruncated)
                                        "${uiState.fileAttachment!!.text.length} / ${uiState.fileAttachment!!.originalLength} 字（已截取）"
                                    else "${uiState.fileAttachment!!.text.length} 字",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { viewModel.clearFileAttachment() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "移除文件",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 生图意图提示条
                    if (uiState.imageHintVisible) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "检测到生图意图，是否生成图片？",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "生成",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { viewModel.confirmImageHint() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "忽略",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { viewModel.dismissImageHintAndSendChat() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    ChatInputBar(
                        value = inputText,
                        onValueChange = { inputText = it },
                        onSend = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText.trim())
                                inputText = ""
                            }
                        },
                        onCancel = { viewModel.cancelGeneration() },
                        onImageClick = { showAttachmentSheet = true },
                        onRoundtableClick = { showRoundtableSheet = true },
                        onChatModelClick = { showModelSwitchSheet = true },
                        onImageModelClick = { showImageModelSwitchSheet = true },
                        onKnowledgeClick = { showKnowledgeSheet = true },
                        showImageButton = true,
                        isConnected = uiState.isConnected,
                        isGenerating = uiState.isGenerating,
                        showInputCancelButton = uiState.isGenerating && !uiState.isStreaming,
                        currentChatModel = uiState.currentChatModel,
                        currentImageModel = uiState.currentImageModel,
                        knowledgeEnabled = uiState.knowledgeEnabled,
                        selectedKnowledgeCount = uiState.selectedKnowledgeDocumentIds.count { selectedId ->
                            knowledgeDocuments.any { it.id == selectedId && it.isReadyKnowledgeDocument() }
                        },
                        isImageMode = uiState.isImageMode,
                        onImageModeToggle = {
                            if (uiState.isImageMode) viewModel.exitImageMode()
                            else viewModel.enterImageMode()
                        }
                    )
                }
            },
            containerColor = if (hasBackground)
                Color.Transparent
            else MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (messages.isEmpty() && uiState.currentSessionId == null) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "输入消息开始对话",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        verticalArrangement = Arrangement.Top
                    ) {
                        item { Spacer(modifier = Modifier.height(8.dp)) }

                        items(messages, key = { it.id }) { message ->
                            when {
                                message.role == "user" -> {
                                    UserMessageBubble(
                                        text = message.content ?: "",
                                        attachmentFileName = message.attachmentFileName
                                    )
                                }
                                message.messageType == "roundtable" && message.status == "running" -> {
                                    TextMessageBubble(
                                        text = message.content ?: "",
                                        isStreaming = uiState.isStreaming,
                                        onCancel = { viewModel.cancelGeneration() }
                                    )
                                }
                                message.messageType == "roundtable" && message.status == "success" -> {
                                    val text = message.content.orEmpty()
                                    TextMessageBubble(
                                        text = text,
                                        onCopy = { copyTextToClipboard(context, text) },
                                        onShare = {
                                            shareReplyAsImage(
                                                scope = scope,
                                                context = context,
                                                question = findPreviousUserMessage(messages, message)?.content,
                                                answer = text
                                            )
                                        }
                                    )
                                }
                                message.messageType == "roundtable" && message.status == "failed" -> {
                                    ErrorMessageBubble(
                                        errorMessage = message.errorMessage ?: "圆桌讨论失败",
                                        onRetry = {}
                                    )
                                }
                                message.messageType == "text" && message.status == "running" -> {
                                    TextMessageBubble(
                                        text = message.content ?: "",
                                        isStreaming = uiState.isStreaming,
                                        onCancel = { viewModel.cancelGeneration() }
                                    )
                                }
                                message.messageType == "text" && message.status == "success" -> {
                                    val text = message.content.orEmpty()
                                    val retryAction: (() -> Unit)? = if (uiState.isGenerating) {
                                        null
                                    } else {
                                        { retryFromPreviousUserMessage(messages, message, viewModel, context) }
                                    }
                                    val branchAction: (() -> Unit)? = if (uiState.isGenerating) {
                                        null
                                    } else {
                                        {
                                            scope.launch {
                                                val branchSessionId = viewModel.branchFromMessage(message.id)
                                                if (branchSessionId != null) {
                                                    onNavigateToSession(branchSessionId)
                                                }
                                            }
                                        }
                                    }
                                    TextMessageBubble(
                                        text = text,
                                        onCopy = { copyTextToClipboard(context, text) },
                                        onShare = {
                                            shareReplyAsImage(
                                                scope = scope,
                                                context = context,
                                                question = findPreviousUserMessage(messages, message)?.content,
                                                answer = text
                                            )
                                        },
                                        onRetry = retryAction,
                                        onBranch = branchAction
                                    )
                                }
                                message.messageType == "text" && message.status == "failed" -> {
                                    ErrorMessageBubble(
                                        errorMessage = message.errorMessage ?: "对话失败",
                                        onRetry = {
                                            val userMsg = messages.lastOrNull {
                                                it.role == "user" && it.createdAt < message.createdAt
                                            }
                                            if (userMsg?.content != null) {
                                                viewModel.sendMessage(userMsg.content)
                                            }
                                        }
                                    )
                                }
                                message.status == "pending" || message.status == "running" -> {
                                    LoadingMessageBubble(onCancel = { viewModel.cancelGeneration() })
                                }
                                message.status == "failed" -> {
                                    ErrorMessageBubble(
                                        errorMessage = message.errorMessage ?: "生成失败",
                                        onRetry = {
                                            val userMsg = messages.lastOrNull {
                                                it.role == "user" && it.createdAt < message.createdAt
                                            }
                                            if (userMsg?.content != null) {
                                                viewModel.sendMessage(userMsg.content)
                                            }
                                        }
                                    )
                                }
                                message.status == "success" -> {
                                    AssistantMessageBubble(
                                        imagePath = message.imageResultPath,
                                        thumbnailPath = message.thumbnailPath,
                                        sourceImagePath = message.imageSourcePath,
                                        revisedPrompt = null,
                                        paramsSummary = message.paramsJson?.let { parseParamsSummary(it) },
                                        onImageClick = {
                                            if (message.imageResultPath != null) {
                                                onNavigateToImageDetail(message.imageResultPath)
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}

private fun copyTextToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("AI回复", text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

private fun shareReplyAsImage(
    scope: CoroutineScope,
    context: Context,
    question: String?,
    answer: String
) {
    scope.launch {
        AiReplyShareImage.createShareImage(context, question, answer)
            .onSuccess { file ->
                runCatching { AiReplyShareImage.shareImage(context, file) }
                    .onFailure { e ->
                        Toast.makeText(context, "分享失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
            }
            .onFailure { e ->
                Toast.makeText(context, "生成分享图片失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }
}

private fun findPreviousUserMessage(
    messages: List<ChatMessageEntity>,
    assistantMessage: ChatMessageEntity
): ChatMessageEntity? = messages.lastOrNull {
    it.role == "user" && it.createdAt < assistantMessage.createdAt
}

private fun retryFromPreviousUserMessage(
    messages: List<ChatMessageEntity>,
    assistantMessage: ChatMessageEntity,
    viewModel: ChatViewModel,
    context: Context
) {
    val userMsg = messages.lastOrNull {
        it.role == "user" && it.createdAt < assistantMessage.createdAt
    }
    if (userMsg?.content != null) {
        viewModel.sendMessage(userMsg.content)
    } else {
        Toast.makeText(context, "找不到可重试的用户消息", Toast.LENGTH_SHORT).show()
    }
}

private fun KnowledgeDocumentEntity.isReadyKnowledgeDocument(): Boolean = status == "ready"

private fun parseParamsSummary(paramsJson: String): String {
    return try {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val map = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(paramsJson)
        buildString {
            map["size"]?.let { append("尺寸:${it.toString().trim('"')} ") }
            map["steps"]?.let { append("Steps:${it} ") }
            map["seed"]?.let { append("Seed:${it} ") }
        }.trim()
    } catch (e: Exception) {
        ""
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onImageClick: () -> Unit,
    onRoundtableClick: () -> Unit,
    onChatModelClick: () -> Unit,
    onImageModelClick: () -> Unit,
    onKnowledgeClick: () -> Unit,
    showImageButton: Boolean,
    isConnected: Boolean,
    isGenerating: Boolean,
    showInputCancelButton: Boolean,
    currentChatModel: String?,
    currentImageModel: String?,
    knowledgeEnabled: Boolean,
    selectedKnowledgeCount: Int,
    isImageMode: Boolean = false,
    onImageModeToggle: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        // 两个模型选择器并排
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 对话模型
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onChatModelClick() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = "对话",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = currentChatModel ?: "选择对话模型",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (currentChatModel != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // 生图模型
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onImageModelClick() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = "生图",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = currentImageModel ?: "选择生图模型",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (currentImageModel != null) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val knowledgeText = when {
                knowledgeEnabled && selectedKnowledgeCount > 0 -> "知识库 · ${selectedKnowledgeCount} 个文档"
                knowledgeEnabled -> "知识库 · 未选择"
                else -> "知识库"
            }
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (knowledgeEnabled) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable(enabled = !isGenerating && !isImageMode) { onKnowledgeClick() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (knowledgeEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isImageMode) "知识库 · 生图不可用" else knowledgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (knowledgeEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = !isGenerating) { onRoundtableClick() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "圆桌",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isImageMode) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onImageModeToggle() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = if (isImageMode) "退出生图模式" else "进入生图模式",
                    modifier = Modifier.size(14.dp),
                    tint = if (isImageMode) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "生图",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isImageMode) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 生图模式指示条
        if (isImageMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "生图模式",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "点击图片按钮退出",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            if (showImageButton) {
                IconButton(onClick = onImageClick) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "附件",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }


            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp)),
                placeholder = {
                    Text(
                        if (isImageMode) "描述你想要的图片..."
                        else "输入消息...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = if (isImageMode)
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = if (isImageMode)
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                maxLines = 5,
                enabled = !isGenerating
            )

            Spacer(modifier = Modifier.width(8.dp))

            if (showInputCancelButton) {
                // 停止按钮
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .clickable { onCancel() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "停止",
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                // 发送按钮
                val canSend = value.isNotBlank() && isConnected && !isGenerating
                val sendColor = if (isImageMode) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.primary

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) sendColor
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable(enabled = canSend) { onSend() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isImageMode) Icons.Default.Image else Icons.Default.Send,
                        contentDescription = if (isImageMode) "生成图片" else "发送",
                        tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (!isConnected) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "网络不可用",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentSheet(
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onFileClick: () -> Unit,
    onImageModeClick: () -> Unit,
    onPhotoSelect: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    var recentPhotos by remember { mutableStateOf<List<Uri>>(emptyList()) }

    LaunchedEffect(Unit) {
        recentPhotos = RecentPhotosLoader.loadRecentPhotos(context, limit = 30)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "选择附件",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 四个按钮：相机、相册、文件、生图
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onCameraClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "相机",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onGalleryClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "相册",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onFileClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "文件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { onImageModeClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "生图",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 最近照片网格
            if (recentPhotos.isNotEmpty()) {
                Text(
                    text = "最近照片",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(recentPhotos) { uri ->
                        Image(
                            painter = rememberAsyncImagePainter(model = uri),
                            contentDescription = null,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onPhotoSelect(uri) },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            } else {
                Text(
                    text = "没有最近照片",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KnowledgeSelectorSheet(
    documents: List<KnowledgeDocumentEntity>,
    enabled: Boolean,
    selectedIds: Set<String>,
    onEnabledChange: (Boolean) -> Unit,
    onToggleDocument: (String) -> Unit,
    onClearSelection: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "本地知识库",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "启用后，发送文本消息时会检索选中文档作为参考资料。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onEnabledChange(!enabled) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = enabled,
                    onCheckedChange = onEnabledChange
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "启用本地知识库",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (documents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "请先在设置中导入文件。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "选择文档",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onClearSelection) {
                        Text("清空")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(documents, key = { it.id }) { document ->
                        val ready = document.isReadyKnowledgeDocument()
                        val checked = document.id in selectedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(enabled = ready) { onToggleDocument(document.id) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                enabled = ready,
                                onCheckedChange = { onToggleDocument(document.id) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = document.fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = when (document.status) {
                                        "ready" -> "${document.charCount} 字 · ${document.chunkCount} 段"
                                        "indexing" -> "正在索引..."
                                        "failed" -> document.errorMessage ?: "导入失败"
                                        else -> document.status
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (document.status == "failed") {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoundtableSheet(
    prompt: String,
    onDismiss: () -> Unit,
    onStart: (List<RoundtableParticipant>, Int) -> Unit,
    viewModel: ChatViewModel
) {
    val sheetState = rememberModalBottomSheetState()
    var apiConfigs by remember { mutableStateOf<List<ApiConfigEntity>>(emptyList()) }
    var selectedParticipants by remember { mutableStateOf<List<RoundtableParticipant>>(emptyList()) }
    var maxRounds by remember { mutableStateOf(2) }

    LaunchedEffect(Unit) {
        apiConfigs = viewModel.getChatApiConfigs()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "圆桌讨论",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "选择 2-4 个对话模型，第一个为 Leader。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "最大轮数：$maxRounds",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (maxRounds > 1) maxRounds-- }) {
                        Text("-")
                    }
                    TextButton(onClick = { if (maxRounds < 5) maxRounds++ }) {
                        Text("+")
                    }
                }
            }

            if (selectedParticipants.isNotEmpty()) {
                Text(
                    text = "Leader：${selectedParticipants.first().configName} / ${selectedParticipants.first().model}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (apiConfigs.isEmpty()) {
                Text(
                    text = "还没有对话配置，请先在设置中添加。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(apiConfigs) { config ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = config.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val models = try {
                                Json.decodeFromString<List<String>>(config.models)
                            } catch (e: Exception) {
                                emptyList()
                            }

                            if (models.isEmpty()) {
                                Text(
                                    text = "该配置没有可用模型",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                models.forEach { model ->
                                    val participant = RoundtableParticipant(
                                        configId = config.id,
                                        configName = config.name,
                                        baseUrl = config.baseUrl,
                                        apiKey = config.apiKey,
                                        model = model,
                                        chatPath = config.chatPath,
                                        chatProtocol = config.chatProtocol
                                    )
                                    val isSelected = selectedParticipants.any { it.key == participant.key }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .clickable {
                                                selectedParticipants = if (isSelected) {
                                                    selectedParticipants.filterNot { it.key == participant.key }
                                                } else if (selectedParticipants.size < 4) {
                                                    selectedParticipants + participant
                                                } else {
                                                    selectedParticipants
                                                }
                                            }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = null
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = model,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isSelected) {
                                                val order = selectedParticipants.indexOfFirst { it.key == participant.key } + 1
                                                Text(
                                                    text = if (order == 1) "发言顺序 $order · Leader" else "发言顺序 $order",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onStart(selectedParticipants, maxRounds) },
                enabled = prompt.isNotBlank() && selectedParticipants.size in 2..4,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("开始讨论")
            }
            if (prompt.isBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "请先在输入框中填写问题。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSwitchSheet(
    currentConfigId: String?,
    currentModel: String?,
    onDismiss: () -> Unit,
    onModelSelect: (configId: String, model: String) -> Unit,
    viewModel: ChatViewModel
) {
    val sheetState = rememberModalBottomSheetState()
    var apiConfigs by remember { mutableStateOf<List<ApiConfigEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        apiConfigs = viewModel.getChatApiConfigs()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "切换模型",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (apiConfigs.isEmpty()) {
                Text(
                    text = "还没有配置，请先在设置中添加",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(apiConfigs) { config ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = config.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            val models = try {
                                Json.decodeFromString<List<String>>(config.models)
                            } catch (e: Exception) {
                                emptyList()
                            }

                            if (models.isEmpty()) {
                                Text(
                                    text = "该配置没有可用模型",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                models.forEach { model ->
                                    val isSelected = currentConfigId == config.id && currentModel == model
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .clickable {
                                                onModelSelect(config.id, model)
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = model,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageModelSwitchSheet(
    currentConfigId: String?,
    currentModel: String?,
    onDismiss: () -> Unit,
    onModelSelect: (configId: String, model: String) -> Unit,
    onParamsClick: () -> Unit,
    onTemplateClick: () -> Unit,
    viewModel: ChatViewModel
) {
    val sheetState = rememberModalBottomSheetState()
    var apiConfigs by remember { mutableStateOf<List<ApiConfigEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        apiConfigs = viewModel.getImageApiConfigs()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "切换生图模型",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onParamsClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("生成参数")
                }
                Button(
                    onClick = onTemplateClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Prompt 模板库")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (apiConfigs.isEmpty()) {
                Text(
                    text = "还没有生图配置，请先在设置中添加",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(apiConfigs) { config ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = config.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = imageProviderDisplayName(config.providerId),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            val models = try {
                                Json.decodeFromString<List<String>>(config.models)
                            } catch (e: Exception) {
                                emptyList()
                            }

                            if (models.isEmpty()) {
                                Text(
                                    text = "该配置没有可用模型",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                models.forEach { model ->
                                    val isSelected = currentConfigId == config.id && currentModel == model
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .clickable {
                                                onModelSelect(config.id, model)
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = model,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
