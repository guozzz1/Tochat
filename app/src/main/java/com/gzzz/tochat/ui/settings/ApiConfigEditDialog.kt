package com.gzzz.tochat.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gzzz.tochat.data.provider.PROVIDER_GPT_IMAGE
import com.gzzz.tochat.data.provider.PROVIDER_GROK
import com.gzzz.tochat.data.provider.imageProviderDisplayName

@Composable
fun ApiConfigEditDialog(
    initialId: String? = null,
    initialName: String = "",
    initialBaseUrl: String = "",
    initialApiKey: String = "",
    initialModels: List<String> = emptyList(),
    configType: String = "chat",
    initialProviderId: String? = null,
    initialChatProtocol: String = "chat_completions",
    onDismiss: () -> Unit,
    onConfirm: (id: String, name: String, baseUrl: String, apiKey: String, models: List<String>, providerId: String?, chatPath: String, chatProtocol: String) -> Unit,
    onRefreshModels: (baseUrl: String, apiKey: String, callback: (List<String>) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var baseUrl by remember { mutableStateOf(initialBaseUrl) }
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var availableModels by remember { mutableStateOf<List<String>>(initialModels) }
    var selectedModels by remember { mutableStateOf(initialModels.toSet()) }
    var customModelName by remember { mutableStateOf("") }
    var chatProtocol by remember { mutableStateOf(initialChatProtocol.ifBlank { "chat_completions" }) }
    var selectedProviderId by remember { mutableStateOf(initialProviderId ?: PROVIDER_GPT_IMAGE) }
    var isLoadingModels by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialId == null) "添加配置" else "编辑配置") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("厂商名称") },
                    placeholder = { Text("例如：OpenAI") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (configType == "image") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "生图渠道",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    listOf(PROVIDER_GPT_IMAGE, PROVIDER_GROK).forEach { providerId ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedProviderId == providerId,
                                onClick = { selectedProviderId = providerId }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = imageProviderDisplayName(providerId),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )

                if (configType == "chat") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "接口格式",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    listOf(
                        "chat_completions" to "OpenAI Chat Completions",
                        "responses" to "OpenAI Responses"
                    ).forEach { (protocol, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = chatProtocol == protocol,
                                onClick = { chatProtocol = protocol }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "模型列表",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            if (baseUrl.isNotBlank() && apiKey.isNotBlank()) {
                                isLoadingModels = true
                                onRefreshModels(baseUrl, apiKey) { models ->
                                    availableModels = (models + selectedModels).distinct()
                                    isLoadingModels = false
                                }
                            }
                        },
                        enabled = baseUrl.isNotBlank() && apiKey.isNotBlank() && !isLoadingModels
                    ) {
                        if (isLoadingModels) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(4.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "刷新模型",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customModelName,
                        onValueChange = { customModelName = it },
                        label = { Text("自定义模型名") },
                        placeholder = { Text("例如：gpt-4o-mini") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        enabled = customModelName.trim().isNotEmpty(),
                        onClick = {
                            val modelName = customModelName.trim()
                            availableModels = (availableModels + modelName).distinct()
                            selectedModels = selectedModels + modelName
                            customModelName = ""
                        }
                    ) {
                        Text("添加")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (availableModels.isEmpty()) {
                    Text(
                        text = "点击刷新按钮获取模型列表，或手动添加模型名",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    availableModels.forEach { model ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedModels.contains(model),
                                onCheckedChange = { checked ->
                                    selectedModels = if (checked) {
                                        selectedModels + model
                                    } else {
                                        selectedModels - model
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = model,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && baseUrl.isNotBlank() && apiKey.isNotBlank()) {
                        onConfirm(
                            initialId ?: java.util.UUID.randomUUID().toString(),
                            name,
                            baseUrl,
                            apiKey,
                            selectedModels.toList(),
                            if (configType == "image") selectedProviderId else null,
                            if (configType == "chat" && chatProtocol == "responses") "v1/responses" else "v1/chat/completions",
                            if (configType == "chat") chatProtocol else "chat_completions"
                        )
                    }
                },
                enabled = name.isNotBlank() && baseUrl.isNotBlank() && apiKey.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
