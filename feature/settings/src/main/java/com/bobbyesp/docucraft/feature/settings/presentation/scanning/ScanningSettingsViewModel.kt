/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.settings.presentation.scanning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobbyesp.docucraft.core.domain.preferences.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What happens to a scan once it is saved: whether it is reviewed, and whether its text is read.
 */
class ScanningSettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    /** `null` until the stored preferences have been read. */
    val state: StateFlow<ScanningSettingsState?> =
        settingsRepository.settings
            .map {
                ScanningSettingsState(
                    reviewsNewScans = it.reviewNewScans,
                    recognizesTextInNewDocuments = it.recognizeTextInNewDocuments,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null,
            )

    fun setReviewNewScans(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setReviewNewScans(enabled) }
    }

    fun setRecognizeTextInNewDocuments(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setRecognizeTextInNewDocuments(enabled) }
    }
}

data class ScanningSettingsState(
    val reviewsNewScans: Boolean,
    val recognizesTextInNewDocuments: Boolean,
)
