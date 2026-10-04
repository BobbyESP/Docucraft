/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.domain.preferences

import com.bobbyesp.docucraft.core.domain.model.FontConfig
import com.bobbyesp.docucraft.core.domain.model.PaletteStyleConfig
import com.bobbyesp.docucraft.core.domain.model.ThemeConfig
import com.bobbyesp.docucraft.core.domain.model.UserPreferences
import com.bobbyesp.docucraft.core.domain.model.ViewerFitMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<UserPreferences>

    suspend fun updateThemeConfig(themeConfig: ThemeConfig)

    suspend fun updateHighContrastMode(enabled: Boolean)

    suspend fun updateDynamicColoring(enabled: Boolean)

    suspend fun updateThemeSeedColor(color: Int)

    suspend fun updatePaletteStyle(paletteStyle: PaletteStyleConfig)

    suspend fun updateDisplayFont(fontConfig: FontConfig)

    suspend fun updateTitleFont(fontConfig: FontConfig)

    suspend fun updateBodyFont(fontConfig: FontConfig)

    suspend fun updateLabelFont(fontConfig: FontConfig)

    suspend fun updateMonospaceFont(fontConfig: FontConfig)

    suspend fun setCompletedOnboarding(completed: Boolean)

    suspend fun setMarqueeTextEnabled(enabled: Boolean)

    /** Whether documents open with the user's viewer defaults rather than the factory ones. */
    suspend fun setViewerDefaultsEnabled(enabled: Boolean)

    suspend fun updateViewerDefaultFitMode(fitMode: ViewerFitMode)

    suspend fun setViewerDefaultNightMode(enabled: Boolean)

    /**
     * Whether documents open where they were left. This only keeps the choice: forgetting what was
     * already remembered is `SetReadingPositionMemoryUseCase`'s job, which is what to call.
     */
    suspend fun setRememberReadingPosition(enabled: Boolean)

    /**
     * Whether documents saved from now on have their text recognized. It changes nothing about the
     * documents there already are: each one keeps what was chosen for it.
     */
    suspend fun setRecognizeTextInNewDocuments(enabled: Boolean)

    /** Whether a scan is shown to the user to be named and organized as soon as it is saved. */
    suspend fun setReviewNewScans(enabled: Boolean)
}
