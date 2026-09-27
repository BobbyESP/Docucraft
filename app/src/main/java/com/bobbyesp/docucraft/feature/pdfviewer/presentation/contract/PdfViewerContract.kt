/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract

import com.bobbyesp.docucraft.core.domain.model.ViewerDisplaySettings
import com.bobbyesp.docucraft.core.domain.model.ViewerFitMode
import com.bobbyesp.docucraft.feature.pdfviewer.domain.actions.canBeHandedOff
import com.bobbyesp.docucraft.feature.pdfviewer.domain.links.BlockReason
import com.bobbyesp.docucraft.feature.pdfviewer.domain.links.LinkAction
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.PageTextState
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.TextUnavailable
import com.bobbyesp.docucraft.feature.shared.domain.BasicDocument
import com.bobbyesp.documentcontent.DocumentSelection
import com.bobbyesp.documentcontent.NormalizedPoint
import com.bobbyesp.documentcontent.NormalizedRect
import com.bobbyesp.documentcontent.PageLink
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
    /**
     * The text of the pages near what is on screen, and of the pages the selection ends on. Read
     * lazily, as the reader moves; a page missing here is not known yet.
     */
    val pageText: Map<Int, PageTextState> = emptyMap(),
    /**
     * The links of the pages on screen and either side, read with their text. As the document has
     * them: what following one does is decided when it is tapped (`ResolveLinkUseCase`).
     */
    val pageLinks: Map<Int, List<PageLink>> = emptyMap(),
    /** The selected text, which may run over several pages. */
    val selection: DocumentSelection? = null,
    /** The link tapped, shown before anything opens (D3); `null` when none is. */
    val linkPreview: LinkPreview? = null,
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

/**
 * A tapped link, waiting for the reader to decide (D3): where it is, to anchor the preview to it,
 * and what following it would do.
 */
data class LinkPreview(val page: Int, val area: NormalizedRect, val action: LinkAction) {
    /** Whether "Open" is on offer: not for a link that was refused. */
    val canOpen: Boolean
        get() =
            action is LinkAction.OpenWeb ||
                action is LinkAction.ComposeEmail ||
                action is LinkAction.Dial

    /** What "Copy link" copies; `null` when there is nothing worth copying. */
    val copyable: String?
        get() =
            when (action) {
                is LinkAction.OpenWeb -> action.url
                is LinkAction.ComposeEmail -> action.address
                is LinkAction.Dial -> action.number
                is LinkAction.Blocked ->
                    action.target.takeUnless { action.reason == BlockReason.OutsideDocument }
                is LinkAction.GoTo -> null
            }
}

sealed interface PdfViewerIntent {
    /**
     * A link on [page] was tapped: an internal one is followed at once; any other is shown first.
     *
     * @param pageCount The document's, to tell an internal link that points past its end.
     */
    data class TapLink(val page: Int, val link: PageLink, val pageCount: Int) : PdfViewerIntent

    data object OpenPreviewedLink : PdfViewerIntent

    data object CopyPreviewedLink : PdfViewerIntent

    data object DismissLinkPreview : PdfViewerIntent

    /** The pages on screen changed, and their text may be needed. */
    data class VisiblePagesChanged(val pages: IntRange) : PdfViewerIntent

    /** The selection is now [selection]: a long press chose a word, or a handle moved. */
    data class Select(val selection: DocumentSelection) : PdfViewerIntent

    data object SelectAll : PdfViewerIntent

    data object ClearSelection : PdfViewerIntent

    data object CopySelection : PdfViewerIntent

    /** A long press found no text to select, for [reason]. */
    data class NothingToSelect(val reason: TextUnavailable) : PdfViewerIntent

    data class SetFitMode(val fitMode: ViewerFitMode) : PdfViewerIntent

    data object ToggleNightMode : PdfViewerIntent

    data object Share : PdfViewerIntent

    data object OpenWith : PdfViewerIntent

    data object Print : PdfViewerIntent
}

sealed interface PdfViewerEffect {
    /** Opening needs the activity (D4). */
    data class OpenLink(val action: LinkAction) : PdfViewerEffect

    /**
     * An internal link, followed at once: scroll to [page] (and [position] on it), and offer the
     * way back to [from], which is why no confirmation is asked.
     */
    data class GoToPage(val page: Int, val position: NormalizedPoint?, val from: Int) :
        PdfViewerEffect

    /** The clipboard belongs to the UI. */
    data class CopyText(val text: String) : PdfViewerEffect

    /** Printing needs the activity, which the ViewModel does not hold. */
    data class Print(val document: ContentRef, val jobName: String) : PdfViewerEffect
}
