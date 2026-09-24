/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase

import com.bobbyesp.docucraft.core.domain.model.ViewerDisplaySettings
import com.bobbyesp.docucraft.core.domain.preferences.SettingsRepository
import com.bobbyesp.docucraft.feature.pdfviewer.domain.model.ViewerDocumentRef
import com.bobbyesp.docucraft.feature.pdfviewer.domain.settings.ViewerSessionSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * What a document is shown with (decision D2): whatever was set for it in this session, otherwise
 * the user's defaults if they turned them on, otherwise the factory settings.
 *
 * A flow rather than a read, so a document not yet touched in this session follows the defaults as
 * they change in Settings, while one the user adjusted keeps what they chose.
 */
class ObserveViewerDisplaySettingsUseCase(
    private val session: ViewerSessionSettings,
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(document: ViewerDocumentRef): Flow<ResolvedViewerDisplay> =
        combine(session.observe(document), settingsRepository.settings) { chosen, preferences ->
                ResolvedViewerDisplay(
                    settings = chosen ?: preferences.viewerDefaults.effective,
                    isChosen = chosen != null,
                )
            }
            .distinctUntilChanged()
}

/**
 * @property settings What the document is shown with.
 * @property isChosen Whether the user set them for this document during the session, rather than
 *   them coming from the defaults. Only a choice needs keeping across a process death: a document
 *   left alone should go on following the defaults.
 */
data class ResolvedViewerDisplay(val settings: ViewerDisplaySettings, val isChosen: Boolean)

/** Remembers, for this session only, what the user set for one document. */
class UpdateViewerDisplaySettingsUseCase(private val session: ViewerSessionSettings) {
    operator fun invoke(document: ViewerDocumentRef, settings: ViewerDisplaySettings) {
        session.set(document, settings)
    }
}
