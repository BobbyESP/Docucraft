package com.bobbyesp.docucraft.core.presentation.theme

import android.util.Log
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.createFontFamilyResolver
import com.bobbyesp.docucraft.core.domain.model.UserPreferences
import com.materialkolor.rememberDynamicMaterialThemeState
import kotlinx.coroutines.CoroutineExceptionHandler

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.sp

@Immutable
data class DocucraftCustomTypography(
    val monospaceCode: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
)

val LocalMonospaceFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }
val LocalDocucraftCustomTypography = staticCompositionLocalOf { DocucraftCustomTypography() }

object DocucraftTheme {
    val customTypography: DocucraftCustomTypography
        @Composable
        get() = LocalDocucraftCustomTypography.current
}

@Suppress("ModifierRequired")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DocucraftTheme(
    userPreferences: UserPreferences = UserPreferences(),
    content: @Composable () -> Unit
) {
    val isDark = userPreferences.themeConfig.isDarkTheme()
    val context = LocalContext.current

    val fontHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("DocucraftTheme", "Error loading font: ${throwable.message}", throwable)
    }

    val useDynamicColoring = userPreferences.useDynamicColoring && isDynamicColoringSupported()

    val colorScheme = if (useDynamicColoring) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        rememberDynamicMaterialThemeState(
            seedColor = Color(userPreferences.themeSeedColor),
            style = userPreferences.paletteStyle.toPaletteStyle(),
            isDark = isDark,
            isAmoled = userPreferences.isHighContrastModeEnabled,
        ).colorScheme
    }

    val displayFont = userPreferences.displayFont.toFontFamily()
    val titleFont = userPreferences.titleFont.toFontFamily()
    val bodyFont = userPreferences.bodyFont.toFontFamily()
    val labelFont = userPreferences.labelFont.toFontFamily()
    val monospaceFont = userPreferences.monospaceFont.toFontFamily() ?: FontFamily.Monospace

    val customTypography = DocucraftCustomTypography(
        monospaceCode = TextStyle(
            fontFamily = monospaceFont,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    )

    CompositionLocalProvider(
        LocalFontFamilyResolver provides createFontFamilyResolver(context, fontHandler),
        LocalMonospaceFontFamily provides monospaceFont,
        LocalDocucraftCustomTypography provides customTypography
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = createTypography(
                displayFont = displayFont,
                titleFont = titleFont,
                bodyFont = bodyFont,
                labelFont = labelFont
            ),
            content = content,
        )
    }
}

