package com.bobbyesp.docucraft.core.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import com.materialkolor.DynamicMaterialThemeState
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicMaterialThemeState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DocucraftTheme(
    themeState: DynamicMaterialThemeState =
        rememberDynamicMaterialThemeState(
            seedColor = MaterialTheme.colorScheme.primary,
            style = PaletteStyle.Expressive,
            isDark = isSystemInDarkTheme(),
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
        ),
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = themeState.colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
