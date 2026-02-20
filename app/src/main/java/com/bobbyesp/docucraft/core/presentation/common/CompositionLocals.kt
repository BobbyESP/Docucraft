package com.bobbyesp.docucraft.core.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import com.bobbyesp.docucraft.core.data.local.preferences.AppPreferencesController
import com.bobbyesp.docucraft.core.data.local.preferences.AppSettings
import com.bobbyesp.docucraft.core.data.local.preferences.theme.DarkThemePreference
import com.bobbyesp.docucraft.core.data.local.preferences.theme.DarkThemePreference.DarkThemeValue
import com.bobbyesp.docucraft.core.presentation.theme.DEFAULT_SEED_COLOR
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.core.presentation.theme.isDynamicColoringSupported
import com.dokar.sonner.ToasterState
import com.dokar.sonner.rememberToasterState
import com.materialkolor.rememberDynamicMaterialThemeState
import com.skydoves.landscapist.coil.LocalCoilImageLoader

val LocalDarkTheme =
    compositionLocalOf<DarkThemePreference> { error("No Dark Theme preferences provided") }
val LocalSeedColor = compositionLocalOf { DEFAULT_SEED_COLOR }
val LocalDynamicColoringSwitch = compositionLocalOf { false }
val LocalOrientation = compositionLocalOf<Int> { error("No orientation provided") }

val LocalAppPreferencesController =
    staticCompositionLocalOf<AppPreferencesController> { error("No settings controller provided") }

val LocalWindowWidthState = staticCompositionLocalOf {
    WindowWidthSizeClass.Compact
} // This value probably will never change, that's why it is static

val LocalSonner = compositionLocalOf<ToasterState> { error("No sonner toaster state provided") }

@Composable
fun AppLocalSettingsProvider(
    windowWidthSize: WindowWidthSizeClass,
    appPreferences: AppPreferencesController,
    imageLoader: ImageLoader,
    sonner: ToasterState = rememberToasterState(),
    content: @Composable () -> Unit
) {
    val seedColor by
    appPreferences
        .getSettingFlow(AppSettings.Theming.THEME_COLOR, null)
        .collectAsStateWithLifecycle(
            initialValue = AppSettings.Theming.THEME_COLOR.defaultValue
        )

    val useDynamicColoring by
    appPreferences
        .getSettingFlow(AppSettings.Theming.USE_DYNAMIC_COLORING, null)
        .collectAsStateWithLifecycle(
            initialValue = AppSettings.Theming.USE_DYNAMIC_COLORING.defaultValue
        )

    val paletteStyleName by
    appPreferences
        .getSettingFlow(AppSettings.Theming.PALETTE_STYLE, null)
        .collectAsStateWithLifecycle(
            initialValue = AppSettings.Theming.PALETTE_STYLE.defaultValue
        )
    val paletteStyle = com.materialkolor.PaletteStyle.valueOf(paletteStyleName)

    val darkThemeValueName by
    appPreferences
        .getSettingFlow(AppSettings.Theming.DARK_THEME_VALUE, null)
        .collectAsStateWithLifecycle(
            initialValue = AppSettings.Theming.DARK_THEME_VALUE.defaultValue
        )

    val darkThemeValue =
        try {
            DarkThemeValue.valueOf(darkThemeValueName)
        } catch (_: IllegalArgumentException) {
            when (darkThemeValueName) {
                "ON" -> DarkThemeValue.DARK
                "OFF" -> DarkThemeValue.LIGHT
                else -> DarkThemeValue.FOLLOW_SYSTEM
            }
        }

    val isHighContrast by
    appPreferences
        .getSettingFlow(AppSettings.Theming.HIGH_CONTRAST, null)
        .collectAsStateWithLifecycle(
            initialValue = AppSettings.Theming.HIGH_CONTRAST.defaultValue
        )

    val darkTheme =
        DarkThemePreference(
            darkThemeValue = darkThemeValue,
            isHighContrastModeEnabled = isHighContrast,
        )

    val config = LocalConfiguration.current
    val context = LocalContext.current

    val isDark = darkTheme.isDarkTheme()

    val colorScheme =
        if (useDynamicColoring && isDynamicColoringSupported()) {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            rememberDynamicMaterialThemeState(
                seedColor = Color(seedColor),
                isDark = isDark,
                style = paletteStyle,
                isAmoled = darkTheme.isHighContrastModeEnabled,
            )
                .colorScheme
        }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme, // Tells the app what dark theme to use
        LocalSeedColor provides
                seedColor, // Tells the app what color to use as seed for the palette
        LocalDynamicColoringSwitch provides
                useDynamicColoring, // Tells the app if it should use dynamic colors or not
        // (Android
        // 12+ feature)
        LocalAppPreferencesController provides appPreferences,
        LocalWindowWidthState provides windowWidthSize,
        LocalOrientation provides config.orientation,
        LocalSonner provides sonner,
        LocalCoilImageLoader provides imageLoader,
    ) {
        DocucraftTheme(colorScheme = colorScheme) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}
