/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation

import androidx.lifecycle.SavedStateHandle
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.StringProvider
import com.bobbyesp.docucraft.core.domain.analytics.AnalyticsEvent
import com.bobbyesp.docucraft.core.domain.model.ViewerDisplaySettings
import com.bobbyesp.docucraft.core.domain.model.ViewerFitMode
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.domain.repository.AnalyticsHelper
import com.bobbyesp.docucraft.core.domain.repository.logScreenView
import com.bobbyesp.docucraft.core.util.events.UiEvent
import com.bobbyesp.docucraft.core.util.viewModel.BaseViewModel
import com.bobbyesp.docucraft.feature.docscanner.domain.sharing.DocumentSharer
import com.bobbyesp.docucraft.feature.pdfviewer.domain.actions.DocumentOpener
import com.bobbyesp.docucraft.feature.pdfviewer.domain.model.ViewerDocumentRef
import com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase.ObserveViewerDisplaySettingsUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase.ObserveViewerDocumentUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase.UpdateViewerDisplaySettingsUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.PdfViewerEffect
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.PdfViewerIntent
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.PdfViewerUiState
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.ViewerDocumentState
import com.bobbyesp.docucraft.feature.shared.domain.BasicDocument
import com.bobbyesp.scanner.ContentRef

/**
 * The viewer's state holder, one per navigation entry (or per external document, in
 * `PdfViewerActivity`).
 *
 * It exists so that what the user chose survives what the composition does not: a rotation used to
 * reset the fit mode and night mode, because they were `remember`ed in the screen.
 *
 * Those settings follow decision D2: remembered per document for the session, in memory. The
 * entry's [SavedStateHandle] keeps a copy, because a process death and restore is still the same
 * session to the user (A1), and the memory does not survive it.
 */
class PdfViewerViewModel(
    private val ref: ViewerDocumentRef,
    private val savedStateHandle: SavedStateHandle,
    observeDocument: ObserveViewerDocumentUseCase,
    observeDisplaySettings: ObserveViewerDisplaySettingsUseCase,
    private val updateDisplaySettings: UpdateViewerDisplaySettingsUseCase,
    private val documentSharer: DocumentSharer,
    private val documentOpener: DocumentOpener,
    private val stringProvider: StringProvider,
    private val analyticsHelper: AnalyticsHelper,
) :
    BaseViewModel<PdfViewerIntent, PdfViewerUiState, PdfViewerEffect>(
        initialState = PdfViewerUiState()
    ) {

    init {
        // Once per opened document, whichever way it was opened.
        analyticsHelper.logScreenView(SCREEN_NAME)

        // Only there after a process death: otherwise the session still has it, or never did.
        restoredDisplaySettings()?.let { updateDisplaySettings(ref, it) }

        launch {
            observeDisplaySettings(ref).collect { resolved ->
                // Whichever entry made the choice: a document reopened in this session inherits it
                // from the session memory, and must keep it across a process death just the same.
                if (resolved.isChosen) keepAcrossProcessDeath(resolved.settings)
                setState { copy(display = resolved.settings) }
            }
        }

        launch {
            observeDocument(ref).collect { document ->
                setState {
                    copy(
                        document =
                            document?.let(ViewerDocumentState::Open) ?: ViewerDocumentState.Gone
                    )
                }
            }
        }
    }

    override fun onHandleIntent(intent: PdfViewerIntent) {
        when (intent) {
            is PdfViewerIntent.SetFitMode -> setFitMode(intent)
            PdfViewerIntent.ToggleNightMode -> toggleNightMode()
            PdfViewerIntent.Share -> handOff { documentSharer.share(it) }
            PdfViewerIntent.OpenWith -> handOff { documentOpener.openWith(it) }
            PdfViewerIntent.Print -> print()
        }
    }

    private fun setFitMode(intent: PdfViewerIntent.SetFitMode) {
        val display = currentState.display ?: return
        choose(display.copy(fitMode = intent.fitMode))
        logSettingChanged(name = "fit_mode", value = intent.fitMode.name)
    }

    private fun toggleNightMode() {
        val display = currentState.display ?: return
        val chosen = display.copy(nightMode = !display.nightMode)
        choose(chosen)
        logSettingChanged(name = "night_mode", value = chosen.nightMode.toString())
    }

    /** Shown at once and remembered for the session; the session memory reports it back. */
    private fun choose(display: ViewerDisplaySettings) {
        setState { copy(display = display) }
        updateDisplaySettings(ref, display)
    }

    /** Against A1: a process death empties the session memory, but not the entry's saved state. */
    private fun keepAcrossProcessDeath(display: ViewerDisplaySettings) {
        savedStateHandle[KEY_FIT_MODE] = display.fitMode.name
        savedStateHandle[KEY_NIGHT_MODE] = display.nightMode
    }

    private fun restoredDisplaySettings(): ViewerDisplaySettings? {
        val fitMode =
            savedStateHandle.get<String>(KEY_FIT_MODE)?.let { name ->
                ViewerFitMode.entries.firstOrNull { it.name == name }
            } ?: return null
        val nightMode = savedStateHandle.get<Boolean>(KEY_NIGHT_MODE) ?: return null
        return ViewerDisplaySettings(fitMode = fitMode, nightMode = nightMode)
    }

    private fun handOff(action: (ContentRef) -> Unit) {
        val document = openDocument() ?: return
        if (!currentState.canHandOff) return

        runCatching { action(ContentRef(document.uri)) }
            .onFailure {
                sendUiEvent(
                    UiEvent.ShowMessage(
                        stringProvider.get(R.string.issue_sharing_doc),
                        NotificationType.Error,
                    )
                )
            }
    }

    private fun print() {
        val document = openDocument() ?: return
        sendEffect(
            PdfViewerEffect.Print(
                document = ContentRef(document.uri),
                jobName = document.title ?: document.filename,
            )
        )
    }

    private fun openDocument(): BasicDocument? =
        (currentState.document as? ViewerDocumentState.Open)?.document

    private fun logSettingChanged(name: String, value: String) {
        analyticsHelper.logEvent(
            AnalyticsEvent(
                type = AnalyticsEvent.Types.PDF_VIEWER_SETTING_CHANGED,
                extras =
                    listOf(
                        AnalyticsEvent.Param(AnalyticsEvent.ParamKeys.SETTING_NAME, name),
                        AnalyticsEvent.Param(AnalyticsEvent.ParamKeys.SETTING_VALUE, value),
                    ),
            )
        )
    }

    private companion object {
        const val SCREEN_NAME = "PdfViewer"
        const val KEY_FIT_MODE = "viewer_fit_mode"
        const val KEY_NIGHT_MODE = "viewer_night_mode"
    }
}
