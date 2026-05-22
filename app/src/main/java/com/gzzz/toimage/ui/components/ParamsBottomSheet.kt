package com.gzzz.toimage.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gzzz.toimage.data.provider.Capabilities

data class GenerationParams(
    val size: String = "1024x1024",
    val steps: Int? = null,
    val seed: Long? = null,
    val cfgScale: Float? = null,
    val batchSize: Int = 1
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ParamsBottomSheet(
    capabilities: Capabilities,
    currentParams: GenerationParams,
    onDismiss: () -> Unit,
    onConfirm: (GenerationParams) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var selectedSize by remember { mutableStateOf(currentParams.size) }
    var steps by remember { mutableFloatStateOf(currentParams.steps?.toFloat() ?: 30f) }
    var seedText by remember { mutableStateOf(currentParams.seed?.toString() ?: "") }
    var cfgScale by remember { mutableFloatStateOf(currentParams.cfgScale ?: 7f) }

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
                text = "生成参数",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 尺寸选择
            Text(
                text = "尺寸",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                capabilities.supportedSizes.forEach { size ->
                    FilterChip(
                        selected = selectedSize == size,
                        onClick = { selectedSize = size },
                        label = { Text(size) }
                    )
                }
            }

            // Steps 滑块 — 仅当 provider 支持时显示
            if (capabilities.supportsSteps) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Steps: ${steps.toInt()}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = steps,
                    onValueChange = { steps = it },
                    valueRange = 1f..100f,
                    steps = 99,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            // CFG Scale 滑块 — 仅当 provider 支持 steps 时显示
            if (capabilities.supportsSteps) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "CFG Scale: ${"%.1f".format(cfgScale)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = cfgScale,
                    onValueChange = { cfgScale = it },
                    valueRange = 1f..30f,
                    steps = 28,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            // Seed — 仅当 provider 支持时显示
            if (capabilities.supportsSeed) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Seed（留空为随机）",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                androidx.compose.material3.OutlinedTextField(
                    value = seedText,
                    onValueChange = { seedText = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("随机") }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            androidx.compose.material3.Button(
                onClick = {
                    onConfirm(
                        GenerationParams(
                            size = selectedSize,
                            steps = if (capabilities.supportsSteps) steps.toInt() else null,
                            seed = if (capabilities.supportsSeed) seedText.toLongOrNull() else null,
                            cfgScale = if (capabilities.supportsSteps) cfgScale else null,
                            batchSize = currentParams.batchSize
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("确认")
            }
        }
    }
}
