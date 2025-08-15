package com.bobbyesp.docucraft.core.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.DynamicMaterialExpressiveTheme
import com.materialkolor.DynamicMaterialTheme
import com.materialkolor.DynamicMaterialThemeState
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicMaterialThemeState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

    DynamicMaterialExpressiveTheme(
        state = themeState,
        motionScheme = MotionScheme.expressive(),
        animate = true,
        content = content
    )
}
