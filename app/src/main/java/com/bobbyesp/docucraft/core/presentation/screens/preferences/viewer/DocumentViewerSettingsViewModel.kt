/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.screens.preferences.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobbyesp.docucraft.core.domain.model.ViewerDefaults
import com.bobbyesp.docucraft.core.domain.model.ViewerFitMode
import com.bobbyesp.docucraft.core.domain.preferences.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The document viewer's defaults (decision D2). Only the stored defaults: what the user changes
 * while reading lives in the viewer's session memory and never comes through here.
 */
class DocumentViewerSettingsViewModel(private val settingsRepository: SettingsRepository) :
    ViewModel() {

    /** `null` until the stored preferences have been read. */
    val defaults: StateFlow<ViewerDefaults?> =
        settingsRepository.settings
            .map { it.viewerDefaults }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null,
            )

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setViewerDefaultsEnabled(enabled) }
    }

    fun setFitMode(fitMode: ViewerFitMode) {
        viewModelScope.launch { settingsRepository.updateViewerDefaultFitMode(fitMode) }
    }

    fun setNightMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setViewerDefaultNightMode(enabled) }
    }
}
