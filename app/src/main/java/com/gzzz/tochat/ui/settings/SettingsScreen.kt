package com.gzzz.tochat.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.gzzz.tochat.data.local.ApiConfigEntity
import com.gzzz.tochat.data.local.KnowledgeDocumentEntity
import com.gzzz.tochat.data.provider.imageProviderDisplayName
import com.gzzz.tochat.data.repository.SettingsRepository
import com.gzzz.tochat.util.ImagePickerUtil
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

private const val SETTINGS_PAGE_HOME = "home"
private const val SETTINGS_PAGE_APPEARANCE = "appearance"
private const val SETTINGS_PAGE_CONFIGS = "configs"
private const val GITHUB_REPOSITORY_URL = "https://github.com/guozzz1/Tochat"
private const val GITHUB_ISSUES_URL = "https://github.com/guozzz1/Tochat/issues/new"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    hasBackground: Boolean = false
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var currentPage by rememberSaveable { mutableStateOf(SETTINGS_PAGE_HOME) }
    var storageInfo by remember { mutableStateOf<com.gzzz.tochat.data.storage.StorageInfo?>(null) }

    val backgroundPath by viewModel.backgroundPath.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    val cropLauncher = rememberLauncherForActivityResult(
        contract = CropImageContract()
    ) { result ->
        if (result.isSuccessful) {
            val uri = result.uriContent
            if (uri != null) {
                val path = ImagePickerUtil.processPickedImage(context, uri) { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
                if (path != null) {
                    viewModel.setBackgroundPath(path)
                }
            }
        } else {
            Toast.makeText(context, "裁剪取消", Toast.LENGTH_SHORT).show()
        }
    }

    val bgPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            cropLauncher.launch(
                CropImageContractOptions(
                    uri = uri,
                    cropImageOptions = CropImageOptions(
                        guidelines = CropImageView.Guidelines.ON,
                        aspectRatioX = 9,
                        aspectRatioY = 16,
                        outputCompressQuality = 85
                    )
                )
            )
        }
    }

    val chatConfigs by viewModel.chatConfigs.collectAsState()
    val imageConfigs by viewModel.imageConfigs.collectAsState()
    val knowledgeDocuments by viewModel.knowledgeDocuments.collectAsState()
    val isImportingKnowledge by viewModel.isImportingKnowledge.collectAsState()

    val knowledgeFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importKnowledgeDocument(uri) { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    var showEditDialog by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf<ApiConfigEntity?>(null) }
    var editingType by remember { mutableStateOf("chat") }
    var chatConfigsExpanded by rememberSaveable { mutableStateOf(false) }
    var imageConfigsExpanded by rememberSaveable { mutableStateOf(false) }
    var knowledgeExpanded by rememberSaveable { mutableStateOf(false) }

    if (showEditDialog) {
        ApiConfigEditDialog(
            initialId = editingConfig?.id,
            initialName = editingConfig?.name ?: "",
            initialBaseUrl = editingConfig?.baseUrl ?: "",
            initialApiKey = editingConfig?.apiKey ?: "",
            initialModels = editingConfig?.models?.let {
                try {
                    Json.decodeFromString<List<String>>(it)
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList(),
            configType = editingType,
            initialProviderId = editingConfig?.providerId,
            initialChatProtocol = editingConfig?.chatProtocol ?: "chat_completions",
            onDismiss = {
                showEditDialog = false
                editingConfig = null
            },
            onConfirm = { id, name, baseUrl, apiKey, models, providerId, chatPath, chatProtocol ->
                val saved = viewModel.saveApiConfig(
                    id = id,
                    name = name,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    models = models,
                    type = editingType,
                    providerId = providerId,
                    chatPath = chatPath,
                    chatProtocol = chatProtocol
                )
                if (saved) {
                    showEditDialog = false
                    editingConfig = null
                    Toast.makeText(context, "配置已保存", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Base URL 必须以 http:// 或 https:// 开头", Toast.LENGTH_SHORT).show()
                }
            },
            onRefreshModels = { url, key, callback ->
                viewModel.fetchModelsForConfig(url, key, callback)
            }
        )
    }

    LaunchedEffect(Unit) {
        storageInfo = viewModel.getStorageInfo()
    }

    BackHandler(enabled = currentPage != SETTINGS_PAGE_HOME) {
        currentPage = SETTINGS_PAGE_HOME
    }

    val pageTitle = when (currentPage) {
        SETTINGS_PAGE_APPEARANCE -> "外观"
        SETTINGS_PAGE_CONFIGS -> "配置管理"
        else -> "设置"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pageTitle) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentPage == SETTINGS_PAGE_HOME) {
                                onNavigateBack()
                            } else {
                                currentPage = SETTINGS_PAGE_HOME
                            }
                        }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (hasBackground)
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    else MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = if (hasBackground) Color.Transparent else MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            when (currentPage) {
                SETTINGS_PAGE_APPEARANCE -> {
                    AppearanceSettingsPage(
                        themeMode = themeMode,
                        backgroundPath = backgroundPath,
                        hasBackground = hasBackground,
                        onThemeModeChange = viewModel::setThemeMode,
                        onChooseBackground = {
                            bgPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onClearBackground = viewModel::clearBackgroundPath
                    )
                }

                SETTINGS_PAGE_CONFIGS -> {
                    ConfigManagementPage(
                        chatConfigs = chatConfigs,
                        imageConfigs = imageConfigs,
                        chatConfigsExpanded = chatConfigsExpanded,
                        imageConfigsExpanded = imageConfigsExpanded,
                        hasBackground = hasBackground,
                        onToggleChatConfigs = { chatConfigsExpanded = !chatConfigsExpanded },
                        onToggleImageConfigs = { imageConfigsExpanded = !imageConfigsExpanded },
                        onAddChatConfig = {
                            chatConfigsExpanded = true
                            editingConfig = null
                            editingType = "chat"
                            showEditDialog = true
                        },
                        onAddImageConfig = {
                            imageConfigsExpanded = true
                            editingConfig = null
                            editingType = "image"
                            showEditDialog = true
                        },
                        onEditChatConfig = { config ->
                            editingConfig = config
                            editingType = "chat"
                            showEditDialog = true
                        },
                        onEditImageConfig = { config ->
                            editingConfig = config
                            editingType = "image"
                            showEditDialog = true
                        },
                        onDeleteConfig = { config ->
                            viewModel.deleteApiConfig(config.id)
                            Toast.makeText(context, "配置已删除", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                else -> {
                    SettingsHomePage(
                        chatConfigCount = chatConfigs.size,
                        imageConfigCount = imageConfigs.size,
                        knowledgeDocuments = knowledgeDocuments,
                        knowledgeExpanded = knowledgeExpanded,
                        isImportingKnowledge = isImportingKnowledge,
                        storageInfo = storageInfo,
                        themeMode = themeMode,
                        hasBackgroundImage = backgroundPath != null,
                        hasBackground = hasBackground,
                        onOpenAppearance = { currentPage = SETTINGS_PAGE_APPEARANCE },
                        onOpenConfigs = { currentPage = SETTINGS_PAGE_CONFIGS },
                        onToggleKnowledge = { knowledgeExpanded = !knowledgeExpanded },
                        onImportKnowledge = {
                            knowledgeExpanded = true
                            knowledgeFileLauncher.launch(
                                arrayOf(
                                    "application/pdf",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "text/plain",
                                    "text/markdown",
                                    "text/x-markdown"
                                )
                            )
                        },
                        onDeleteKnowledgeDocument = { document ->
                            viewModel.deleteKnowledgeDocument(document.id)
                            Toast.makeText(context, "知识库文件已删除", Toast.LENGTH_SHORT).show()
                        },
                        onClearHistory = {
                            viewModel.clearAllHistory()
                            storageInfo = null
                            Toast.makeText(context, "历史记录已清空", Toast.LENGTH_SHORT).show()
                        },
                        onClearImages = {
                            viewModel.clearAllImages()
                            coroutineScope.launch {
                                storageInfo = viewModel.getStorageInfo()
                            }
                            Toast.makeText(context, "图片已清理", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsHomePage(
    chatConfigCount: Int,
    imageConfigCount: Int,
    knowledgeDocuments: List<KnowledgeDocumentEntity>,
    knowledgeExpanded: Boolean,
    isImportingKnowledge: Boolean,
    storageInfo: com.gzzz.tochat.data.storage.StorageInfo?,
    themeMode: String,
    hasBackgroundImage: Boolean,
    hasBackground: Boolean,
    onOpenAppearance: () -> Unit,
    onOpenConfigs: () -> Unit,
    onToggleKnowledge: () -> Unit,
    onImportKnowledge: () -> Unit,
    onDeleteKnowledgeDocument: (KnowledgeDocumentEntity) -> Unit,
    onClearHistory: () -> Unit,
    onClearImages: () -> Unit
) {
    SettingsSectionTitle("功能")
    SettingsNavigationGroup(hasBackground = hasBackground) {
        SettingsNavigationRow(
            icon = Icons.Default.Settings,
            title = "配置管理",
            subtitle = "对话配置 · 生图配置",
            trailingText = "对话 $chatConfigCount · 生图 $imageConfigCount",
            iconTint = MaterialTheme.colorScheme.primary,
            onClick = onOpenConfigs
        )
        Divider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsNavigationRow(
            icon = Icons.Default.Tune,
            title = "外观",
            subtitle = "主题模式 · 聊天背景",
            trailingText = "${themeModeLabel(themeMode)}${if (hasBackgroundImage) " · 已设置背景" else ""}",
            iconTint = MaterialTheme.colorScheme.secondary,
            onClick = onOpenAppearance
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    KnowledgeSettingsSection(
        documents = knowledgeDocuments,
        expanded = knowledgeDocuments.isEmpty() || knowledgeExpanded,
        isImporting = isImportingKnowledge,
        hasBackground = hasBackground,
        onToggle = onToggleKnowledge,
        onImport = onImportKnowledge,
        onDelete = onDeleteKnowledgeDocument
    )

    Spacer(modifier = Modifier.height(24.dp))
    Divider()
    Spacer(modifier = Modifier.height(24.dp))

    StorageManagementSection(
        storageInfo = storageInfo,
        hasBackground = hasBackground,
        onClearHistory = onClearHistory,
        onClearImages = onClearImages
    )

    Spacer(modifier = Modifier.height(24.dp))
    Divider()
    Spacer(modifier = Modifier.height(24.dp))

    AboutSection(hasBackground = hasBackground)
}

@Composable
private fun ConfigManagementPage(
    chatConfigs: List<ApiConfigEntity>,
    imageConfigs: List<ApiConfigEntity>,
    chatConfigsExpanded: Boolean,
    imageConfigsExpanded: Boolean,
    hasBackground: Boolean,
    onToggleChatConfigs: () -> Unit,
    onToggleImageConfigs: () -> Unit,
    onAddChatConfig: () -> Unit,
    onAddImageConfig: () -> Unit,
    onEditChatConfig: (ApiConfigEntity) -> Unit,
    onEditImageConfig: (ApiConfigEntity) -> Unit,
    onDeleteConfig: (ApiConfigEntity) -> Unit
) {
    ConfigSectionHeader(
        title = "对话配置",
        count = chatConfigs.size,
        countText = "已配置 ${chatConfigs.size} 个",
        expanded = chatConfigs.isEmpty() || chatConfigsExpanded,
        color = MaterialTheme.colorScheme.primary,
        onToggle = onToggleChatConfigs
    )

    Spacer(modifier = Modifier.height(12.dp))

    if (chatConfigs.isEmpty() || chatConfigsExpanded) {
        if (chatConfigs.isEmpty()) {
            EmptyStateCard(
                title = "还没有配置",
                subtitle = "点击下方按钮添加第一个配置",
                hasBackground = hasBackground
            )
        } else {
            chatConfigs.forEach { config ->
                ApiConfigCard(
                    config = config,
                    hasBackground = hasBackground,
                    onEdit = { onEditChatConfig(config) },
                    onDelete = { onDeleteConfig(config) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedButton(
        onClick = onAddChatConfig,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("添加对话配置")
    }

    Spacer(modifier = Modifier.height(24.dp))
    Divider()
    Spacer(modifier = Modifier.height(24.dp))

    ConfigSectionHeader(
        title = "生图配置",
        count = imageConfigs.size,
        countText = "已配置 ${imageConfigs.size} 个",
        expanded = imageConfigs.isEmpty() || imageConfigsExpanded,
        color = MaterialTheme.colorScheme.secondary,
        onToggle = onToggleImageConfigs
    )

    Spacer(modifier = Modifier.height(12.dp))

    if (imageConfigs.isEmpty() || imageConfigsExpanded) {
        if (imageConfigs.isEmpty()) {
            EmptyStateCard(
                title = "还没有生图配置",
                subtitle = "点击下方按钮添加生图配置",
                hasBackground = hasBackground
            )
        } else {
            imageConfigs.forEach { config ->
                ApiConfigCard(
                    config = config,
                    hasBackground = hasBackground,
                    onEdit = { onEditImageConfig(config) },
                    onDelete = { onDeleteConfig(config) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedButton(
        onClick = onAddImageConfig,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("添加生图配置")
    }
}

@Composable
private fun AppearanceSettingsPage(
    themeMode: String,
    backgroundPath: String?,
    hasBackground: Boolean,
    onThemeModeChange: (String) -> Unit,
    onChooseBackground: () -> Unit,
    onClearBackground: () -> Unit
) {
    SettingsSectionTitle("主题模式")

    SettingsNavigationGroup(hasBackground = hasBackground) {
        listOf(
            SettingsRepository.THEME_MODE_SYSTEM to "跟随系统",
            SettingsRepository.THEME_MODE_LIGHT to "浅色模式",
            SettingsRepository.THEME_MODE_DARK to "深色模式"
        ).forEachIndexed { index, (mode, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onThemeModeChange(mode) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = themeMode == mode,
                    onClick = { onThemeModeChange(mode) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (index < 2) {
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
    Divider()
    Spacer(modifier = Modifier.height(24.dp))

    SettingsSectionTitle("聊天背景")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = settingsCardColor(hasBackground)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (backgroundPath != null) {
                    Image(
                        painter = rememberAsyncImagePainter(model = File(backgroundPath)),
                        contentDescription = "背景预览",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                OutlinedButton(
                    onClick = onChooseBackground,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("选择背景")
                }

                if (backgroundPath != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onClearBackground,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("恢复默认")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "选择后可裁剪合适区域，背景会自动调暗。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun KnowledgeSettingsSection(
    documents: List<KnowledgeDocumentEntity>,
    expanded: Boolean,
    isImporting: Boolean,
    hasBackground: Boolean,
    onToggle: () -> Unit,
    onImport: () -> Unit,
    onDelete: (KnowledgeDocumentEntity) -> Unit
) {
    ConfigSectionHeader(
        title = "本地知识库",
        count = documents.size,
        countText = "已导入 ${documents.size} 个",
        expanded = expanded,
        color = MaterialTheme.colorScheme.primary,
        onToggle = onToggle
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = "支持 TXT、Markdown、PDF、DOCX，内容仅保存在本机。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(12.dp))

    if (expanded) {
        if (documents.isEmpty()) {
            EmptyStateCard(
                title = "还没有导入知识库文件",
                subtitle = "导入文件后，聊天时可以选择作为参考资料",
                hasBackground = hasBackground
            )
        } else {
            documents.forEach { document ->
                KnowledgeDocumentCard(
                    document = document,
                    hasBackground = hasBackground,
                    onDelete = { onDelete(document) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedButton(
        onClick = onImport,
        enabled = !isImporting,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(if (isImporting) "导入中..." else "导入知识库文件")
    }
}

@Composable
private fun StorageManagementSection(
    storageInfo: com.gzzz.tochat.data.storage.StorageInfo?,
    hasBackground: Boolean,
    onClearHistory: () -> Unit,
    onClearImages: () -> Unit
) {
    SettingsSectionTitle("存储管理")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = settingsCardColor(hasBackground)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (storageInfo != null) {
                StorageInfoRow("图片数量", "${storageInfo.imageCount} 张")
                StorageInfoRow("图片大小", storageInfo.generationsSizeFormatted)
                StorageInfoRow("总占用", storageInfo.totalSizeFormatted)
            } else {
                Text(
                    text = "加载中...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row {
                OutlinedButton(
                    onClick = onClearHistory,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("清空历史记录")
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = onClearImages,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("清理图片")
                }
            }
        }
    }
}

@Composable
private fun AboutSection(hasBackground: Boolean) {
    val context = LocalContext.current

    SettingsSectionTitle("关于")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = settingsCardColor(hasBackground)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "ToChat v1.0.0",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "AI 对话工具",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsNavigationRow(
                icon = Icons.Default.Code,
                title = "GitHub",
                trailingText = "guozzz1/Tochat",
                iconTint = MaterialTheme.colorScheme.onSurface,
                onClick = { openExternalUrl(context, GITHUB_REPOSITORY_URL) }
            )
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsNavigationRow(
                icon = Icons.Default.BugReport,
                title = "报告问题",
                iconTint = MaterialTheme.colorScheme.error,
                onClick = { openExternalUrl(context, GITHUB_ISSUES_URL) }
            )
        }
    }
}

@Composable
private fun SettingsNavigationGroup(
    hasBackground: Boolean,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = settingsCardColor(hasBackground)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            content()
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailingText: String? = null,
    iconTint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!trailingText.isNullOrBlank()) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun openExternalUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开链接: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun EmptyStateCard(
    title: String,
    subtitle: String,
    hasBackground: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = settingsMutedCardColor(hasBackground)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StorageInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun KnowledgeDocumentCard(
    document: KnowledgeDocumentEntity,
    hasBackground: Boolean,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = settingsCardColor(hasBackground)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
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
                        }
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ConfigSectionHeader(
    title: String,
    count: Int,
    countText: String,
    expanded: Boolean,
    color: Color,
    onToggle: () -> Unit
) {
    val actionText = if (expanded) "收起$title" else "展开$title"
    val canToggle = count > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = canToggle, onClick = onToggle)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = color
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = countText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(
            onClick = onToggle,
            enabled = canToggle
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = actionText,
                tint = if (canToggle) color else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ApiConfigCard(
    config: ApiConfigEntity,
    hasBackground: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = settingsCardColor(hasBackground)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = config.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = config.baseUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "编辑",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            val models = try {
                Json.decodeFromString<List<String>>(config.models)
            } catch (e: Exception) {
                emptyList()
            }

            if (config.type == "image") {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "渠道：${imageProviderDisplayName(config.providerId)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "接口格式：${if (config.chatProtocol == "responses") "OpenAI Responses" else "OpenAI Chat Completions"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (models.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "已选模型：${models.size} 个",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun settingsCardColor(hasBackground: Boolean): Color {
    return if (hasBackground) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
    } else {
        MaterialTheme.colorScheme.surface
    }
}

@Composable
private fun settingsMutedCardColor(hasBackground: Boolean): Color {
    return if (hasBackground) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
}

private fun themeModeLabel(mode: String): String {
    return when (mode) {
        SettingsRepository.THEME_MODE_LIGHT -> "浅色"
        SettingsRepository.THEME_MODE_DARK -> "深色"
        else -> "跟随系统"
    }
}
