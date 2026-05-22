package com.gzzz.toimage.ui.chat

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.gzzz.toimage.ui.components.AssistantMessageBubble
import com.gzzz.toimage.ui.components.ErrorMessageBubble
import com.gzzz.toimage.ui.components.LoadingMessageBubble
import com.gzzz.toimage.ui.components.ParamsBottomSheet
import com.gzzz.toimage.ui.components.TextMessageBubble
import com.gzzz.toimage.ui.components.UserMessageBubble
import com.gzzz.toimage.ui.session.SessionDrawerContent
import com.gzzz.toimage.ui.template.TemplateSheet
import com.gzzz.toimage.util.ImagePickerUtil
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    sessionId: String?,
    onNavigateToSettings: () -> Unit,
    onNavigateToSession: (String) -> Unit,
    onNavigateToImageDetail: (String) -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var showParamsSheet by remember { mutableStateOf(false) }
    var showTemplateSheet by remember { mutableStateOf(false) }
    var showImageSourceSheet by remember { mutableStateOf(false) }
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

    // 图片来源选择弹窗
    if (showImageSourceSheet) {
        ImageSourceSheet(
            onGalleryClick = {
                showImageSourceSheet = false
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onCameraClick = {
                showImageSourceSheet = false
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
            onDismiss = { showImageSourceSheet = false }
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

    LaunchedEffect(uiState.isDrawerOpen) {
        if (uiState.isDrawerOpen) drawerState.open() else drawerState.close()
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                SessionDrawerContent(
                    sessions = sessions,
                    currentSessionId = uiState.currentSessionId,
                    onSessionClick = { id ->
                        viewModel.closeDrawer()
                        onNavigateToSession(id)
                    },
                    onNewSession = {
                        scope.launch {
                            viewModel.closeDrawer()
                            val sessionId = viewModel.createNewSession()
                            if (sessionId != null) {
                                onNavigateToSession(sessionId)
                            } else {
                                onNavigateToSession("")
                            }
                        }
                    },
                    onDeleteSession = { viewModel.deleteSession(it) },
                    onSettingsClick = {
                        viewModel.closeDrawer()
                        onNavigateToSettings()
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
                        IconButton(onClick = { viewModel.toggleDrawer() }) {
                            Icon(Icons.Default.Menu, contentDescription = "菜单")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .navigationBarsPadding()
                        .imePadding()
                ) {
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

                    ChatInputBar(
                        value = inputText,
                        onValueChange = { inputText = it },
                        onSend = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText.trim())
                                inputText = ""
                            }
                        },
                        onImageClick = { showImageSourceSheet = true },
                        onParamsClick = { showParamsSheet = true },
                        onTemplateClick = { showTemplateSheet = true },
                        showImageButton = true,
                        isConnected = uiState.isConnected,
                        isGenerating = uiState.isGenerating
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background
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
                                    UserMessageBubble(text = message.content ?: "")
                                }
                                message.messageType == "text" && message.status == "running" -> {
                                    TextMessageBubble(
                                        text = message.content ?: "",
                                        isStreaming = uiState.isStreaming
                                    )
                                }
                                message.messageType == "text" && message.status == "success" -> {
                                    TextMessageBubble(text = message.content ?: "")
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
    onImageClick: () -> Unit,
    onParamsClick: () -> Unit,
    onTemplateClick: () -> Unit,
    showImageButton: Boolean,
    isConnected: Boolean,
    isGenerating: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            if (showImageButton) {
                IconButton(onClick = onImageClick) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = "图生图",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onParamsClick) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = "参数",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onTemplateClick) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = "模板",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp)),
                placeholder = {
                    Text(
                        "输入消息或描述你想要的图片...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                maxLines = 5,
                enabled = !isGenerating
            )

            Spacer(modifier = Modifier.width(8.dp))

            val canSend = value.isNotBlank() && isConnected && !isGenerating

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSend) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable(enabled = canSend) { onSend() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "发送",
                    tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
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
private fun ImageSourceSheet(
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit,
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
                text = "选择图片来源",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onGalleryClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "相册",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onCameraClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "拍照",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
