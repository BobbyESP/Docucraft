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
import com.bobbyesp.docucraft.core.data.local.preferences.AppSettings
import com.bobbyesp.docucraft.core.domain.preferences.AppPreferencesController
import com.bobbyesp.docucraft.core.domain.repository.AnalyticsHelper
import com.bobbyesp.docucraft.core.domain.repository.InAppNotificationsService
import com.bobbyesp.docucraft.core.presentation.preferences.theme.DarkThemePreference
import com.bobbyesp.docucraft.core.presentation.preferences.theme.DarkThemePreference.DarkThemeValue
import com.bobbyesp.docucraft.core.presentation.theme.DEFAULT_SEED_COLOR
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.core.presentation.theme.isDynamicColoringSupported
import com.materialkolor.rememberDynamicMaterialThemeState
import com.skydoves.landscapist.coil.LocalCoilImageLoader
import com.skydoves.landscapist.components.LocalImageComponent
import com.skydoves.landscapist.components.imageComponent
import com.skydoves.landscapist.crossfade.CrossfadePlugin

val LocalDarkTheme =
    compositionLocalOf<DarkThemePreference> { error("No Dark Theme preferences provided") }
val LocalSeedColor = compositionLocalOf { DEFAULT_SEED_COLOR }
val LocalDynamicColoringSwitch = compositionLocalOf { false }
val LocalOrientation = compositionLocalOf<Int> { error("No orientation provided") }
val LocalWindowWidthState = staticCompositionLocalOf {
    WindowWidthSizeClass.Compact
}

val LocalAppPreferencesController =
    staticCompositionLocalOf<AppPreferencesController> { error("No settings controller provided") }

val LocalNotificationsService = compositionLocalOf<InAppNotificationsService> {
    error("No notifications service provided")
}

val LocalAnalyticsHelper = staticCompositionLocalOf<AnalyticsHelper> {
    error("No analytics helper provided")
}

@Suppress("ModifierRequired")
@Composable
fun AppLocalSettingsProvider(
    windowWidthSize: WindowWidthSizeClass,
    appPreferences: AppPreferencesController,
    imageLoader: ImageLoader,
    inAppNotificationsService: InAppNotificationsService,
    analyticsHelper: AnalyticsHelper,
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
        LocalDarkTheme provides darkTheme,
        LocalSeedColor provides
                seedColor,
        LocalDynamicColoringSwitch provides
                useDynamicColoring,
        LocalAppPreferencesController provides appPreferences,
        LocalWindowWidthState provides windowWidthSize,
        LocalOrientation provides config.orientation,
        LocalNotificationsService provides inAppNotificationsService,
        LocalAnalyticsHelper provides analyticsHelper,
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
