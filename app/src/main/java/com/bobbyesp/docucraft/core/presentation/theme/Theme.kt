package com.bobbyesp.docucraft.core.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicMaterialThemeState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DocucraftTheme(colorScheme: ColorScheme? = null, content: @Composable () -> Unit) {
    val finalColorScheme =
        colorScheme
            ?: rememberDynamicMaterialThemeState(
                    seedColor = Color.Blue,
                    style = PaletteStyle.Expressive,
                    isDark = isSystemInDarkTheme(),
                )
                .colorScheme

    MaterialExpressiveTheme(
        colorScheme = finalColorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
