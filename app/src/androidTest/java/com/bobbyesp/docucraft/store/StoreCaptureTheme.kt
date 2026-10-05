/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.store

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import coil.ImageLoader
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.model.UserPreferences
import com.bobbyesp.docucraft.core.presentation.common.LocalAnalyticsHelper
import com.bobbyesp.docucraft.core.presentation.common.LocalDarkTheme
import com.bobbyesp.docucraft.core.presentation.common.LocalNotificationsService
import com.bobbyesp.docucraft.core.presentation.common.LocalSettingsRepository
import com.bobbyesp.docucraft.core.presentation.navigation.DocucraftNavDisplay
import com.bobbyesp.docucraft.core.presentation.navigation.openDocumentId
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.rememberOverlaySceneStrategy
import com.bobbyesp.docucraft.core.presentation.navigation.pane.sharingTheWindow
import com.bobbyesp.docucraft.core.presentation.navigation.rememberNavigator
import com.bobbyesp.docucraft.core.presentation.screens.preferences.settingsSection
import com.bobbyesp.docucraft.core.presentation.theme.createTypography
import com.bobbyesp.docucraft.core.presentation.theme.toFontFamily
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.homeSection
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.pdfViewerSection
import com.skydoves.landscapist.coil.LocalCoilImageLoader
import org.koin.core.Koin

/**
 * The brand's two colors, and the tones between them the screens need. Paper and Ink are the Brand
 * Book's, the same values the store graphics are drawn with (`scripts/store/layout.json`); the rest
 * are steps from one to the other, so that a screen is made of nothing else.
 */
internal object Brand {
    val Paper = Color(0xFFFFFDF0)
    val Ink = Color(0xFF34322C)
    val InkMuted = Color(0xFF6E6A60)
    val Sheet = Color(0xFFFFFFFB)
    val Line = Color(0xFFD3CEBD)

    val Light: ColorScheme =
        lightColorScheme(
            primary = Ink,
            onPrimary = Paper,
            primaryContainer = Color(0xFFE7E2CC),
            onPrimaryContainer = Ink,
            inversePrimary = Line,
            secondary = Color(0xFF5F5B4E),
            onSecondary = Paper,
            secondaryContainer = Color(0xFFEAE4CE),
            onSecondaryContainer = Color(0xFF23211B),
            tertiary = Color(0xFF55604A),
            onTertiary = Paper,
            tertiaryContainer = Color(0xFFDDE3CE),
            onTertiaryContainer = Color(0xFF1B2114),
            background = Paper,
            onBackground = Ink,
            surface = Paper,
            onSurface = Ink,
            surfaceVariant = Color(0xFFEBE6D2),
            onSurfaceVariant = InkMuted,
            surfaceTint = Ink,
            inverseSurface = Ink,
            inverseOnSurface = Paper,
            outline = Color(0xFF8B8779),
            outlineVariant = Line,
            surfaceBright = Paper,
            surfaceDim = Color(0xFFE2DDCA),
            surfaceContainerLowest = Sheet,
            surfaceContainerLow = Color(0xFFFAF7E8),
            surfaceContainer = Color(0xFFF5F2E1),
            surfaceContainerHigh = Color(0xFFEFEBDA),
            surfaceContainerHighest = Color(0xFFE9E5D3),
            primaryFixed = Color(0xFFE7E2CC),
            primaryFixedDim = Line,
            onPrimaryFixed = Ink,
            onPrimaryFixedVariant = Color(0xFF4C4A42),
            secondaryFixed = Color(0xFFEAE4CE),
            secondaryFixedDim = Line,
            onSecondaryFixed = Color(0xFF23211B),
            onSecondaryFixedVariant = Color(0xFF5F5B4E),
            tertiaryFixed = Color(0xFFDDE3CE),
            tertiaryFixedDim = Color(0xFFC1C8B1),
            onTertiaryFixed = Color(0xFF1B2114),
            onTertiaryFixedVariant = Color(0xFF55604A),
        )

    /** Ink for the page and Paper for what is written on it: the viewer at night. */
    val Dark: ColorScheme =
        darkColorScheme(
            primary = Paper,
            onPrimary = Ink,
            primaryContainer = Color(0xFF55524A),
            onPrimaryContainer = Paper,
            inversePrimary = Ink,
            secondary = Line,
            onSecondary = Ink,
            secondaryContainer = Color(0xFF4C4A42),
            onSecondaryContainer = Color(0xFFEAE4CE),
            tertiary = Color(0xFFC1C8B1),
            onTertiary = Color(0xFF2B3222),
            tertiaryContainer = Color(0xFF414A37),
            onTertiaryContainer = Color(0xFFDDE3CE),
            background = Ink,
            onBackground = Paper,
            surface = Ink,
            onSurface = Paper,
            surfaceVariant = Color(0xFF4C4A42),
            onSurfaceVariant = Color(0xFFC4BFAF),
            surfaceTint = Paper,
            inverseSurface = Paper,
            inverseOnSurface = Ink,
            outline = Color(0xFF969284),
            outlineVariant = Color(0xFF55524A),
            surfaceBright = Color(0xFF55524A),
            surfaceDim = Color(0xFF282621),
            surfaceContainerLowest = Color(0xFF282621),
            surfaceContainerLow = Color(0xFF393730),
            surfaceContainer = Color(0xFF3E3C35),
            surfaceContainerHigh = Color(0xFF45433B),
            surfaceContainerHighest = Color(0xFF4C4A42),
            primaryFixed = Color(0xFFE7E2CC),
            primaryFixedDim = Line,
            onPrimaryFixed = Ink,
            onPrimaryFixedVariant = Color(0xFF4C4A42),
            secondaryFixed = Color(0xFFEAE4CE),
            secondaryFixedDim = Line,
            onSecondaryFixed = Color(0xFF23211B),
            onSecondaryFixedVariant = Color(0xFF5F5B4E),
            tertiaryFixed = Color(0xFFDDE3CE),
            tertiaryFixedDim = Color(0xFFC1C8B1),
            onTertiaryFixed = Color(0xFF1B2114),
            onTertiaryFixedVariant = Color(0xFF55604A),
        )
}

/**
 * What the app's activities put around its screens, with the brand's colors where the app's theme
 * would put the user's.
 *
 * A theme of its own rather than the app's: the app builds its colors from a seed, which gives a
 * scheme near a color and never that color, and the store has to show Paper and Ink themselves.
 * Type, shapes and motion are the app's. A screen about something with a color of its own, such as
 * a folder, keeps the brand's colors too: its theme builds on the app's, which is not here.
 *
 * @param koin The graph the screens read from, started by [startCaptureGraph].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StoreCaptureTheme(koin: Koin, dark: Boolean, content: @Composable () -> Unit) {
    val typography = remember {
        val fonts = UserPreferences()
        createTypography(
            displayFont = fonts.displayFont.toFontFamily(),
            titleFont = fonts.titleFont.toFontFamily(),
            bodyFont = fonts.bodyFont.toFontFamily(),
            labelFont = fonts.labelFont.toFontFamily(),
        )
    }
    val resources = LocalResources.current
    CompositionLocalProvider(
        LocalResources provides remember(resources) { ReleaseNameResources(resources) },
        LocalDarkTheme provides dark,
        LocalSettingsRepository provides remember(koin) { koin.get() },
        LocalNotificationsService provides remember(koin) { koin.get() },
        LocalAnalyticsHelper provides remember(koin) { koin.get() },
        LocalCoilImageLoader provides remember(koin) { koin.get<ImageLoader>() },
    ) {
        MaterialExpressiveTheme(
            colorScheme = if (dark) Brand.Dark else Brand.Light,
            motionScheme = MotionScheme.expressive(),
            typography = typography,
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            ) {
                content()
            }
        }
    }
}

/**
 * The app's resources with the name the published app has. The screenshots are taken in the debug
 * build, the only one a test can run in, and that one calls itself "Docucraft Debug" to be told
 * apart on a developer's phone: it is the one thing on screen that a user would not see.
 */
@Suppress("DEPRECATION")
private class ReleaseNameResources(base: Resources) :
    Resources(base.assets, base.displayMetrics, base.configuration) {
    override fun getText(id: Int): CharSequence =
        if (id == R.string.app_name) "Docucraft" else super.getText(id)
}

/**
 * The app's shell on a back stack given whole: the same display, strategies and destinations as
 * `DocucraftApp`, so that a screen is reached with the stack a user would have under it, without
 * tapping the way there.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun StoreCaptureApp(stack: List<NavKey>) {
    val backStack = rememberNavBackStack(*stack.toTypedArray())
    val navigator = rememberNavigator(backStack)
    val overlayStrategy = rememberOverlaySceneStrategy<NavKey>()
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()
    val sceneStrategies =
        remember(overlayStrategy, listDetailStrategy) {
            listOf(overlayStrategy, listDetailStrategy.sharingTheWindow())
        }

    DocucraftNavDisplay(
        backStack = backStack,
        navigator = navigator,
        sceneStrategies = sceneStrategies,
        entryProvider =
            entryProvider {
                homeSection(navigator, selectedDocumentId = backStack.openDocumentId())
                pdfViewerSection(navigator)
                settingsSection(navigator)
            },
    )
}
