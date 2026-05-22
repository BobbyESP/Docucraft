package com.bobbyesp.docucraft.core.presentation.components.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.core.domain.model.PaletteStyleConfig
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.core.presentation.theme.toPaletteStyle
import com.materialkolor.rememberDynamicColorScheme

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PaletteStylePicker(
    selectedStyle: PaletteStyleConfig,
    seedColor: Color,
    isDark: Boolean,
    isAmoled: Boolean,
    onStyleSelect: (PaletteStyleConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PaletteStyleConfig.entries.forEach { styleConfig ->
            val isSelected = styleConfig == selectedStyle
            val style = remember(styleConfig) { styleConfig.toPaletteStyle() }
            val scheme = rememberDynamicColorScheme(
                seedColor = seedColor,
                isDark = isDark,
                isAmoled = isAmoled,
                style = style
            )

            val colorList = remember(scheme) {
                listOf(
                    scheme.primary,
                    scheme.primaryContainer,
                    scheme.secondary,
                    scheme.secondaryContainer,
                    scheme.tertiary,
                    scheme.tertiaryContainer,
                )
            }

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(if (isSelected) MaterialShapes.VerySunny.toShape() else CircleShape)
                    .clickable {
                        if (!isSelected) {
                            onStyleSelect(styleConfig)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val sweepAngle = 360f / colorList.size
                    colorList.forEachIndexed { index, color ->
                        drawArc(
                            color = color,
                            startAngle = index * sweepAngle,
                            sweepAngle = sweepAngle,
                            useCenter = true
                        )
                    }
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.2f))
                    )
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun PaletteStylePickerPreview() {
    DocucraftTheme {
        PaletteStylePicker(
            selectedStyle = PaletteStyleConfig.Rainbow,
            seedColor = Color(0xFF6200EE),
            isDark = false,
            isAmoled = false,
            onStyleSelect = {}
        )
    }
}