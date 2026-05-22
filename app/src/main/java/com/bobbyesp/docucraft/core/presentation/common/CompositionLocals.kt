package com.bobbyesp.docucraft.core.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import com.bobbyesp.docucraft.core.domain.model.UserPreferences
import com.bobbyesp.docucraft.core.domain.preferences.SettingsRepository
import com.bobbyesp.docucraft.core.domain.repository.AnalyticsHelper
import com.bobbyesp.docucraft.core.domain.repository.InAppNotificationsService
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.core.presentation.theme.isDarkTheme
import com.skydoves.landscapist.coil.LocalCoilImageLoader

val LocalDarkTheme =
    compositionLocalOf<Boolean> { false }
val LocalOrientation = compositionLocalOf<Int> { error("No orientation provided") }
val LocalWindowWidthState = staticCompositionLocalOf {
    WindowWidthSizeClass.Compact
}

val LocalSettingsRepository =
    staticCompositionLocalOf<SettingsRepository> { error("No settings repository provided") }

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
    settingsRepository: SettingsRepository,
    initialUserPreferences: UserPreferences,
    imageLoader: ImageLoader,
    inAppNotificationsService: InAppNotificationsService,
    analyticsHelper: AnalyticsHelper,

    content: @Composable () -> Unit
) {
    val userPreferences by settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = initialUserPreferences
    )

    val config = LocalConfiguration.current
    val isDark = userPreferences.themeConfig.isDarkTheme()

    CompositionLocalProvider(
        LocalDarkTheme provides isDark,
        LocalSettingsRepository provides settingsRepository,
        LocalWindowWidthState provides windowWidthSize,
        LocalOrientation provides config.orientation,
        LocalNotificationsService provides inAppNotificationsService,
        LocalAnalyticsHelper provides analyticsHelper,
        LocalCoilImageLoader provides imageLoader,
    ) {
        DocucraftTheme(userPreferences = userPreferences) {
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
