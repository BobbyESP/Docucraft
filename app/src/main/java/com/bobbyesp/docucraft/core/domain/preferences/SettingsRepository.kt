package com.bobbyesp.docucraft.core.domain.preferences

import com.bobbyesp.docucraft.core.domain.model.FontConfig
import com.bobbyesp.docucraft.core.domain.model.PaletteStyleConfig
import com.bobbyesp.docucraft.core.domain.model.ThemeConfig
import com.bobbyesp.docucraft.core.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<UserPreferences>

    suspend fun updateThemeConfig(themeConfig: ThemeConfig)
    suspend fun updateHighContrastMode(enabled: Boolean)
    suspend fun updateDynamicColoring(enabled: Boolean)
    suspend fun updateThemeSeedColor(color: Int)
    suspend fun updatePaletteStyle(paletteStyle: PaletteStyleConfig)
    suspend fun updateFontConfig(fontConfig: FontConfig)
    suspend fun setCompletedOnboarding(completed: Boolean)
    suspend fun setMarqueeTextEnabled(enabled: Boolean)
}
