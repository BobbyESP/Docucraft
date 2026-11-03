/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract

import com.bobbyesp.docucraft.core.domain.model.ViewerDisplaySettings
import com.bobbyesp.docucraft.core.domain.model.ViewerFitMode
import com.bobbyesp.docucraft.feature.pdfviewer.domain.actions.canBeHandedOff
import com.bobbyesp.docucraft.feature.pdfviewer.domain.links.BlockReason
import com.bobbyesp.docucraft.feature.pdfviewer.domain.links.LinkAction
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.ViewerLoadError
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.search.DocumentSearch
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.PageTextState
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.TextUnavailable
import com.bobbyesp.docucraft.library.domain.model.BasicDocument
import com.bobbyesp.docucraft.library.domain.model.ReadingPosition
import com.bobbyesp.documentcontent.DocumentSelection
import com.bobbyesp.documentcontent.NormalizedPoint
import com.bobbyesp.documentcontent.NormalizedRect
import com.bobbyesp.documentcontent.PageLink
import com.bobbyesp.scanner.ContentRef

/**
 * What the viewer shows. Pan, zoom, the current page and whether the bars show are not here: they
 * are pure UI state, kept in the composition (`PdfViewerState`).
 */
data class PdfViewerUiState(
    val document: ViewerDocumentState = ViewerDocumentState.Loading,
    /** `null` until known; the document is not laid out before, to avoid laying it out twice. */
    val display: ViewerDisplaySettings? = null,
    /** `null` until known where to open the document, so the pages are laid out once. */
    val start: ViewerStart? = null,
    /** Text of the pages near the screen and of those the selection ends on; missing = not read. */
    val pageText: Map<Int, PageTextState> = emptyMap(),
    /** Links of the pages on screen and either side, as the document has them. */
    val pageLinks: Map<Int, List<PageLink>> = emptyMap(),
    /** The selected text, which may span several pages. */
    val selection: DocumentSelection? = null,
    /** The tapped link, shown before anything opens (D3); `null` when none. */
    val linkPreview: LinkPreview? = null,
    /** The open search and what it has found; `null` when none is open. */
    val search: DocumentSearch? = null,
    /**
     * Whether *Save to Docucraft* is on offer: the document is another app's and only referenced.
     */
    val canSaveToLibrary: Boolean = false,
    /** Its file is being copied; asking again would start a second copy. */
    val isSavingToLibrary: Boolean = false,
) {
    /** The document, once there is everything needed to show it. */
    val readyDocument: BasicDocument?
        get() =
            (document as? ViewerDocumentState.Open)?.document?.takeIf {
                display != null && start != null
            }

    /** Whether Share and Open with are on offer. */
    val canHandOff: Boolean
        get() =
            (document as? ViewerDocumentState.Open)?.document?.let {
                ContentRef(it.uri).canBeHandedOff()
            } == true
}

/**
 * Where the document opens.
 *
 * @property position Where the reader left it; `null` opens it at its start.
 */
data class ViewerStart(val position: ReadingPosition?)

/**
 * What is known about the document the viewer points at. "Not yet" and "gone" are distinct: the
 * first waits, the second leaves the screen.
 */
sealed interface ViewerDocumentState {

    /** No answer yet. */
    data object Loading : ViewerDocumentState

    /** The document no longer exists, so the viewer has nothing left to show. */
    data object Gone : ViewerDocumentState

    data class Open(val document: BasicDocument) : ViewerDocumentState
}

/** A tapped link awaiting the reader's decision (D3), with its [area] on [page] to anchor to. */
data class LinkPreview(val page: Int, val area: NormalizedRect, val action: LinkAction) {
    /** Whether "Open" is on offer; not for a refused link. */
    val canOpen: Boolean
        get() =
            action is LinkAction.OpenWeb ||
                action is LinkAction.ComposeEmail ||
                action is LinkAction.Dial

    /** What "Copy link" copies; `null` when there is nothing to copy. */
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
     * @param pageCount The document's page count, to detect an internal link past the end.
     */
    data class TapLink(val page: Int, val link: PageLink, val pageCount: Int) : PdfViewerIntent

    data object OpenPreviewedLink : PdfViewerIntent

    data object CopyPreviewedLink : PdfViewerIntent

    data object DismissLinkPreview : PdfViewerIntent

    /** The document loaded and has [pageCount] pages. */
    data class DocumentLoaded(val pageCount: Int) : PdfViewerIntent

    /** The document could not be shown, for [error]. */
    data class DocumentFailedToLoad(val error: ViewerLoadError) : PdfViewerIntent

    /** The reader has settled at [position] or is leaving; never sent for every scroll frame. */
    data class ReadingPositionChanged(val position: ReadingPosition) : PdfViewerIntent

    /** The pages on screen changed, and their text may be needed. */
    data class VisiblePagesChanged(val pages: IntRange) : PdfViewerIntent

    /** The selection is now [selection]: a long press chose a word, or a handle moved. */
    data class Select(val selection: DocumentSelection) : PdfViewerIntent

    data object SelectAll : PdfViewerIntent

    data object ClearSelection : PdfViewerIntent

    data object CopySelection : PdfViewerIntent

    /** A long press found no text to select, for [reason]. */
    data class NothingToSelect(val reason: TextUnavailable) : PdfViewerIntent

    /**
     * The search text changed to [query] while the reader is at [fromPage], where the first match
     * is looked for. The query already searched changes nothing.
     */
    data class SearchQueryChanged(val query: String, val fromPage: Int) : PdfViewerIntent

    /** Go to the next or previous match, wrapping at either end. */
    data class SearchStep(val forward: Boolean) : PdfViewerIntent

    /** The search is over: its matches and the text read for it are dropped. */
    data object CloseSearch : PdfViewerIntent

    /** Lights the matches of [query] without the find bar, as when arriving from a search. */
    data class ShowMatches(val query: String, val fromPage: Int) : PdfViewerIntent

    data class SetFitMode(val fitMode: ViewerFitMode) : PdfViewerIntent

    data object ToggleNightMode : PdfViewerIntent

    /** Keep this other-app document in the library. */
    data object SaveToLibrary : PdfViewerIntent

    data object Share : PdfViewerIntent

    data object OpenWith : PdfViewerIntent

    data object Print : PdfViewerIntent
}

sealed interface PdfViewerEffect {
    /** Opening needs the activity (D4). */
    data class OpenLink(val action: LinkAction) : PdfViewerEffect

    /**
     * Scroll to an internal link's target: [page] and [position] on it. The way back to [from] is
     * offered, so no confirmation is asked.
     */
    data class GoToPage(val page: Int, val position: NormalizedPoint?, val from: Int) :
        PdfViewerEffect

    /** The document being saved is already in the library; the screen asks about a second copy. */
    data class ConfirmSaveCopy(val documentUuid: String) : PdfViewerEffect

    /** The library already keeps this content as [documentUuid]; offer it without interrupting. */
    data class AlreadyInLibrary(val documentUuid: String) : PdfViewerEffect

    /**
     * Bring the match at [area] of [page] (normalized to it) into view, unless it already is. A
     * one-off command, not state.
     */
    data class RevealMatch(val page: Int, val area: NormalizedRect) : PdfViewerEffect

    /** The clipboard belongs to the UI. */
    data class CopyText(val text: String) : PdfViewerEffect

    /** Printing needs the activity, which the ViewModel does not hold. */
    data class Print(val document: ContentRef, val jobName: String) : PdfViewerEffect
}
