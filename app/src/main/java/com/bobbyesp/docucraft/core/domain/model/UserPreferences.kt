/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.domain.model

import androidx.compose.runtime.Immutable
import com.bobbyesp.docucraft.core.presentation.theme.DEFAULT_SEED_COLOR

@Immutable
data class UserPreferences(
    val themeConfig: ThemeConfig = ThemeConfig.FOLLOW_SYSTEM,
    val useDynamicColoring: Boolean = true,
    val themeSeedColor: Int = DEFAULT_SEED_COLOR,
    val paletteStyle: PaletteStyleConfig = PaletteStyleConfig.Vibrant,
    val isHighContrastModeEnabled: Boolean = false,
    val displayFont: FontConfig = FontConfig.DMSerifDisplay,
    val titleFont: FontConfig = FontConfig.DMSerifText,
    val bodyFont: FontConfig = FontConfig.Inter,
    val labelFont: FontConfig = FontConfig.DMSans,
    val monospaceFont: FontConfig = FontConfig.JetBrainsMono,
    val completedOnboarding: Boolean = false,
    val marqueeTextEnabled: Boolean = true,
    val viewerDefaults: ViewerDefaults = ViewerDefaults(),
    /** Whether a document is opened where it was left the last time. */
    val rememberReadingPosition: Boolean = true,
    /**
     * Whether a document saved from now on has the text of its image-only pages recognized. Off
     * until the user chooses: recognition takes time and battery.
     */
    val recognizeTextInNewDocuments: Boolean = false,
    /**
     * Whether a scan is shown to the user as soon as it is saved, to be named and organized. On
     * until the user turns it off; without it a scan is saved as it comes, as it always was.
     */
    val reviewNewScans: Boolean = true,
)

enum class ThemeConfig {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
}

enum class PaletteStyleConfig {
    Vibrant,
    Expressive,
    FruitSalad,
    Monochrome,
    Rainbow,
    TonalSpot,
}

enum class FontConfig {
    System,
    DMSerifDisplay,
    DMSerifText,
    Inter,
    DMSans,
    Roboto,
    Montserrat,
    GoogleSansFlex,
    JetBrainsMono,
    FiraCode,
}
