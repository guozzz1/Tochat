package com.gzzz.toimage.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.gzzz.toimage.util.ImagePickerUtil
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    hasBackground: Boolean = false
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // 对话配置状态（必填）
    var chatDisplayName by remember { mutableStateOf("") }
    var chatBaseUrl by remember { mutableStateOf("") }
    var chatApiKey by remember { mutableStateOf("") }
    var chatModel by remember { mutableStateOf("") }
    var chatExpanded by remember { mutableStateOf(false) }

    // 生图配置状态（可选）
    var imageDisplayName by remember { mutableStateOf("") }
    var imageBaseUrl by remember { mutableStateOf("") }
    var imageApiKey by remember { mutableStateOf("") }
    var imageModel by remember { mutableStateOf("") }
    var imageExpanded by remember { mutableStateOf(false) }

    var storageInfo by remember { mutableStateOf<com.gzzz.toimage.data.storage.StorageInfo?>(null) }

    val backgroundPath by viewModel.backgroundPath.collectAsState()

    // 裁剪 launcher
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

    // 选择图片 launcher
    val bgPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            // 启动裁剪
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

    val imageModels by viewModel.imageModels.collectAsState()
    val chatModels by viewModel.chatModels.collectAsState()
    val isLoadingImageModels by viewModel.isLoadingImageModels.collectAsState()
    val isLoadingChatModels by viewModel.isLoadingChatModels.collectAsState()

    // 加载现有配置
    LaunchedEffect(Unit) {
        val config = viewModel.getCurrentConfig()
        if (config != null) {
            chatDisplayName = config.chat.displayName
            chatBaseUrl = config.chat.baseUrl
            chatApiKey = config.chat.apiKey
            chatModel = config.chat.model
            imageDisplayName = config.image.displayName
            imageBaseUrl = config.image.baseUrl
            imageApiKey = config.image.apiKey
            imageModel = config.image.model
        }
        storageInfo = viewModel.getStorageInfo()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
        containerColor = if (hasBackground)
            androidx.compose.ui.graphics.Color.Transparent
        else MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 对话配置（必填）
            Text(
                text = "对话配置",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = chatDisplayName,
                        onValueChange = { chatDisplayName = it },
                        label = { Text("渠道名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = chatBaseUrl,
                        onValueChange = { chatBaseUrl = it },
                        label = { Text("Base URL") },
                        placeholder = { Text("https://api.example.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = chatApiKey,
                        onValueChange = { chatApiKey = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 模型选择下拉 + 刷新按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = chatExpanded && chatModels.isNotEmpty(),
                            onExpandedChange = { chatExpanded = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            TextField(
                                value = chatModel,
                                onValueChange = { chatModel = it },
                                label = { Text("对话模型") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                singleLine = true,
                                readOnly = false,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = chatExpanded && chatModels.isNotEmpty())
                                },
                                colors = ExposedDropdownMenuDefaults.textFieldColors()
                            )

                            ExposedDropdownMenu(
                                expanded = chatExpanded && chatModels.isNotEmpty(),
                                onDismissRequest = { chatExpanded = false }
                            ) {
                                chatModels.forEach { modelId ->
                                    DropdownMenuItem(
                                        text = { Text(modelId, fontSize = 14.sp) },
                                        onClick = {
                                            chatModel = modelId
                                            chatExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                viewModel.fetchChatModels(chatBaseUrl, chatApiKey)
                            },
                            enabled = chatBaseUrl.isNotBlank() && chatApiKey.isNotBlank() && !isLoadingChatModels
                        ) {
                            if (isLoadingChatModels) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(4.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "加载模型列表",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(24.dp))

            // 生图配置（可选）
            Text(
                text = "生图配置（可选）",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = imageDisplayName,
                        onValueChange = { imageDisplayName = it },
                        label = { Text("渠道名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = imageBaseUrl,
                        onValueChange = { imageBaseUrl = it },
                        label = { Text("Base URL") },
                        placeholder = { Text("https://api.example.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = imageApiKey,
                        onValueChange = { imageApiKey = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 模型选择下拉 + 刷新按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = imageExpanded && imageModels.isNotEmpty(),
                            onExpandedChange = { imageExpanded = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            TextField(
                                value = imageModel,
                                onValueChange = { imageModel = it },
                                label = { Text("生图模型") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                singleLine = true,
                                readOnly = false,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = imageExpanded && imageModels.isNotEmpty())
                                },
                                colors = ExposedDropdownMenuDefaults.textFieldColors()
                            )

                            ExposedDropdownMenu(
                                expanded = imageExpanded && imageModels.isNotEmpty(),
                                onDismissRequest = { imageExpanded = false }
                            ) {
                                imageModels.forEach { modelId ->
                                    DropdownMenuItem(
                                        text = { Text(modelId, fontSize = 14.sp) },
                                        onClick = {
                                            imageModel = modelId
                                            imageExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                viewModel.fetchImageModels(imageBaseUrl, imageApiKey)
                            },
                            enabled = imageBaseUrl.isNotBlank() && imageApiKey.isNotBlank() && !isLoadingImageModels
                        ) {
                            if (isLoadingImageModels) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(4.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "加载模型列表",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 保存按钮
            Button(
                onClick = {
                    viewModel.saveProvider(
                        imageConfig = com.gzzz.toimage.domain.model.ServiceConfig(
                            displayName = imageDisplayName,
                            baseUrl = imageBaseUrl.trimEnd('/'),
                            apiKey = imageApiKey,
                            model = imageModel
                        ),
                        chatConfig = com.gzzz.toimage.domain.model.ServiceConfig(
                            displayName = chatDisplayName,
                            baseUrl = chatBaseUrl.trimEnd('/'),
                            apiKey = chatApiKey,
                            model = chatModel
                        )
                    )
                    Toast.makeText(context, "配置已保存", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = chatBaseUrl.isNotBlank() && chatApiKey.isNotBlank()
            ) {
                Text("保存配置")
            }

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(24.dp))

            // 聊天背景
            Text(
                text = "聊天背景",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (backgroundPath != null) {
                            Image(
                                painter = rememberAsyncImagePainter(model = File(backgroundPath!!)),
                                contentDescription = "背景预览",
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        OutlinedButton(
                            onClick = {
                                bgPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            Text("选择背景")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        if (backgroundPath != null) {
                            OutlinedButton(
                                onClick = { viewModel.clearBackgroundPath() },
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
                        text = "选择后可裁剪合适区域，背景会自动调暗",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(24.dp))

            // 存储管理
            Text(
                text = "存储管理",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (storageInfo != null) {
                        val info = storageInfo!!
                        StorageInfoRow("图片数量", "${info.imageCount} 张")
                        StorageInfoRow("图片大小", info.generationsSizeFormatted)
                        StorageInfoRow("总占用", info.totalSizeFormatted)
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
                            onClick = {
                                viewModel.clearAllHistory()
                                storageInfo = null
                                Toast.makeText(context, "历史记录已清空", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("清空历史记录")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedButton(
                            onClick = {
                                viewModel.clearAllImages()
                                coroutineScope.launch {
                                    storageInfo = viewModel.getStorageInfo()
                                }
                                Toast.makeText(context, "图片已清理", Toast.LENGTH_SHORT).show()
                            },
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

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(24.dp))

            // 关于
            Text(
                text = "关于",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ToChat v1.0.0",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "AI 对话工具",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
