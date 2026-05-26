package com.gzzz.tochat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gzzz.tochat.data.provider.Capabilities

data class GenerationParams(
    val size: String = "auto",
    val steps: Int? = null,
    val seed: Long? = null,
    val cfgScale: Float? = null,
    val batchSize: Int = 1
)

data class AspectRatioOption(
    val label: String,
    val ratio: Float?,  // null means auto/智能
    val size: String
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

    val aspectRatioOptions = listOf(
        AspectRatioOption("智能", null, "auto"),
        AspectRatioOption("1:1", 1f, "1024x1024"),
        AspectRatioOption("3:4", 3f / 4f, "768x1024"),
        AspectRatioOption("4:3", 4f / 3f, "1024x768"),
        AspectRatioOption("9:16", 9f / 16f, "576x1024"),
        AspectRatioOption("16:9", 16f / 9f, "1024x576")
    )

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

            // 比例选择
            Text(
                text = "比例",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                aspectRatioOptions.forEach { option ->
                    AspectRatioItem(
                        option = option,
                        isSelected = selectedSize == option.size,
                        onClick = { selectedSize = option.size }
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

@Composable
fun AspectRatioItem(
    option: AspectRatioOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    // 计算框的尺寸，保持合理大小
    val boxWidth = 52.dp
    val boxHeight = when (option.ratio) {
        null -> boxWidth  // 智能模式用正方形
        else -> {
            val height = (boxWidth.value / option.ratio).dp
            height.coerceIn(36.dp, 80.dp)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(boxWidth)
                .height(boxHeight)
                .clip(RoundedCornerShape(6.dp))
                .border(2.dp, borderColor, RoundedCornerShape(6.dp))
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            if (option.ratio == null) {
                Text(
                    text = "AI",
                    fontSize = 11.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = option.label,
            fontSize = 11.sp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
