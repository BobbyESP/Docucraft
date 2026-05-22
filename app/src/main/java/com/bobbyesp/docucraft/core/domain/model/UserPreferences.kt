package com.bobbyesp.docucraft.core.domain.model

import androidx.compose.runtime.Immutable
import com.bobbyesp.docucraft.core.presentation.theme.DEFAULT_SEED_COLOR

@Immutable
data class UserPreferences(
    val themeConfig: ThemeConfig = ThemeConfig.FOLLOW_SYSTEM,
    val isHighContrastModeEnabled: Boolean = false,
    val useDynamicColoring: Boolean = true,
    val themeSeedColor: Int = DEFAULT_SEED_COLOR,
    val paletteStyle: PaletteStyleConfig = PaletteStyleConfig.Vibrant,
    val fontConfig: FontConfig = FontConfig.GoogleSansFlex,
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
}
