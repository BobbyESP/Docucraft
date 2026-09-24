/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation

import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.StringProvider
import com.bobbyesp.docucraft.core.domain.analytics.AnalyticsEvent
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.domain.repository.AnalyticsHelper
import com.bobbyesp.docucraft.core.domain.repository.logScreenView
import com.bobbyesp.docucraft.core.util.events.UiEvent
import com.bobbyesp.docucraft.core.util.viewModel.BaseViewModel
import com.bobbyesp.docucraft.feature.docscanner.domain.sharing.DocumentSharer
import com.bobbyesp.docucraft.feature.pdfviewer.domain.actions.DocumentOpener
import com.bobbyesp.docucraft.feature.pdfviewer.domain.model.ViewerDocumentRef
import com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase.ObserveViewerDocumentUseCase
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
 */
class PdfViewerViewModel(
    ref: ViewerDocumentRef,
    observeDocument: ObserveViewerDocumentUseCase,
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
        setState { copy(fitMode = intent.fitMode) }
        logSettingChanged(name = "fit_mode", value = intent.fitMode.name)
    }

    private fun toggleNightMode() {
        setState { copy(isNightModeEnabled = !isNightModeEnabled) }
        logSettingChanged(name = "night_mode", value = currentState.isNightModeEnabled.toString())
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
    }
}
