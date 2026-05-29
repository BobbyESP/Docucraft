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
    val displayFont: FontConfig = FontConfig.DMSerifText,
    val titleFont: FontConfig = FontConfig.DMSerifText,
    val bodyFont: FontConfig = FontConfig.Inter,
    val labelFont: FontConfig = FontConfig.Inter,
    val monospaceFont: FontConfig = FontConfig.JetBrainsMono,
    val completedOnboarding: Boolean = false,
    val marqueeTextEnabled: Boolean = true,
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
    DMSerifText,
    Inter,
    Roboto,
    Montserrat,
    GoogleSansFlex,
    JetBrainsMono,
    FiraCode,
}
