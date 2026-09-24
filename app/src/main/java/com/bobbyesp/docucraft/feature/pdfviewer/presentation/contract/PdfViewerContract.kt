/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract

import com.bobbyesp.docucraft.core.domain.model.ViewerDisplaySettings
import com.bobbyesp.docucraft.core.domain.model.ViewerFitMode
import com.bobbyesp.docucraft.feature.pdfviewer.domain.actions.canBeHandedOff
import com.bobbyesp.docucraft.feature.shared.domain.BasicDocument
import com.bobbyesp.scanner.ContentRef

/**
 * What the viewer shows. Pan, zoom and the current page are not here: like a list's scroll state,
 * they belong to the composition (`PdfViewerState`), and routing them through here would mean a
 * round trip per frame. Nor is whether the bars are showing, which is presentation alone.
 */
data class PdfViewerUiState(
    val document: ViewerDocumentState = ViewerDocumentState.Loading,
    /**
     * `null` until known. They depend on the session and on the stored defaults, and showing the
     * document before they arrive would lay it out twice.
     */
    val display: ViewerDisplaySettings? = null,
) {
    /** The document, once there is everything needed to show it. */
    val readyDocument: BasicDocument?
        get() = (document as? ViewerDocumentState.Open)?.document?.takeIf { display != null }

    /** Whether Share and Open with are on offer. */
    val canHandOff: Boolean
        get() =
            (document as? ViewerDocumentState.Open)?.document?.let {
                ContentRef(it.uri).canBeHandedOff()
            } == true
}

/**
 * What is known so far about the document the viewer points at.
 *
 * Three answers, because two of them used to be the same `null` and the difference between them is
 * the difference between waiting and leaving. See B3 in `docs/architecture/05-navigation-audit.md`.
 */
sealed interface ViewerDocumentState {

    /** No answer yet. */
    data object Loading : ViewerDocumentState

    /** The document no longer exists, so the viewer has nothing left to show. */
    data object Gone : ViewerDocumentState

    data class Open(val document: BasicDocument) : ViewerDocumentState
}

sealed interface PdfViewerIntent {
    data class SetFitMode(val fitMode: ViewerFitMode) : PdfViewerIntent

    data object ToggleNightMode : PdfViewerIntent

    data object Share : PdfViewerIntent

    data object OpenWith : PdfViewerIntent

    data object Print : PdfViewerIntent
}

sealed interface PdfViewerEffect {
    /** Printing needs the activity, which the ViewModel does not hold. */
    data class Print(val document: ContentRef, val jobName: String) : PdfViewerEffect
}
