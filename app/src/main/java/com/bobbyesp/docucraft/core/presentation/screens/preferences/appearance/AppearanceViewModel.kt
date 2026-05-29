/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.screens.preferences.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobbyesp.docucraft.core.domain.model.FontConfig
import com.bobbyesp.docucraft.core.domain.model.PaletteStyleConfig
import com.bobbyesp.docucraft.core.domain.model.ThemeConfig
import com.bobbyesp.docucraft.core.domain.model.UserPreferences
import com.bobbyesp.docucraft.core.domain.preferences.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AppearanceUiState {
    data object Loading : AppearanceUiState

    data class Success(val preferences: UserPreferences) : AppearanceUiState
}

class AppearanceViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    val uiState: StateFlow<AppearanceUiState> =
        settingsRepository.settings
            .map { AppearanceUiState.Success(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = AppearanceUiState.Loading,
            )

    fun updateThemeConfig(themeConfig: ThemeConfig) {
        viewModelScope.launch { settingsRepository.updateThemeConfig(themeConfig) }
    }

    fun updateHighContrastMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateHighContrastMode(enabled) }
    }

    fun updateDynamicColoring(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateDynamicColoring(enabled) }
    }

    fun updateThemeSeedColor(color: Int) {
        viewModelScope.launch { settingsRepository.updateThemeSeedColor(color) }
    }

    fun updatePaletteStyle(paletteStyle: PaletteStyleConfig) {
        viewModelScope.launch { settingsRepository.updatePaletteStyle(paletteStyle) }
    }

    fun updateDisplayFont(fontConfig: FontConfig) {
        viewModelScope.launch { settingsRepository.updateDisplayFont(fontConfig) }
    }

    fun updateTitleFont(fontConfig: FontConfig) {
        viewModelScope.launch { settingsRepository.updateTitleFont(fontConfig) }
    }

    fun updateBodyFont(fontConfig: FontConfig) {
        viewModelScope.launch { settingsRepository.updateBodyFont(fontConfig) }
    }

    fun updateLabelFont(fontConfig: FontConfig) {
        viewModelScope.launch { settingsRepository.updateLabelFont(fontConfig) }
    }

    fun updateMonospaceFont(fontConfig: FontConfig) {
        viewModelScope.launch { settingsRepository.updateMonospaceFont(fontConfig) }
    }
}
