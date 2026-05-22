package com.gzzz.toimage.ui.template

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gzzz.toimage.data.local.PromptTemplateEntity

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TemplateSheet(
    onDismiss: () -> Unit,
    onSelect: (PromptTemplateEntity) -> Unit,
    viewModel: TemplateViewModel = hiltViewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val templates by viewModel.templates.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredTemplates = templates.filter { template ->
        val matchesCategory = selectedCategory == null || template.category == selectedCategory
        val matchesSearch = searchQuery.isBlank() ||
                template.name.contains(searchQuery, ignoreCase = true) ||
                template.prompt.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesSearch
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Prompt 模板库",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索模板...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 分类标签
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("全部") }
                )
                categories.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = if (selectedCategory == category) null else category },
                        label = { Text(category) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 模板列表
            if (filteredTemplates.isEmpty()) {
                Text(
                    text = "暂无模板",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(400.dp)
                ) {
                    items(filteredTemplates, key = { it.id }) { template ->
                        TemplateItem(
                            template = template,
                            onClick = {
                                onSelect(template)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateItem(
    template: PromptTemplateEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = template.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (template.isBuiltIn) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "内置",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = template.prompt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
