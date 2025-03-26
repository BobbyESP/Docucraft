package com.bobbyesp.docucraft.core.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.DynamicMaterialTheme
import com.materialkolor.DynamicMaterialThemeState
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicMaterialThemeState

@Composable
fun DocucraftTheme(
    themeState: DynamicMaterialThemeState =
        rememberDynamicMaterialThemeState(
            seedColor = Color(DEFAULT_SEED_COLOR),
            style = PaletteStyle.Monochrome,
            isDark = isSystemInDarkTheme(),
        ),
    dynamicColorEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val canUseDynamicColor = dynamicColorEnabled && isDynamicColoringSupported()

    DynamicMaterialTheme(state = themeState, animate = true, content = content)
}
