/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.ViewerChromeState
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.ViewerLoadError
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.rememberViewerChromeState
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.search.ViewerSearchState
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.search.rememberViewerSearchState
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.selection.SelectionDrag
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.selection.rememberSelectionDrag
import com.bobbyesp.docucraft.library.domain.model.ReadingPosition
import com.bobbyesp.documentcontent.NormalizedRect
import com.composepdf.PdfReadingPosition
import com.composepdf.PdfViewerState
import com.composepdf.rememberPdfViewerState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What the viewer's screen holds besides the ViewModel's state: the engine, the bars, the search
 * bar, and what each of them needs from the others.
 */
@Stable
internal class PdfViewerScreenState(
    val pdf: PdfViewerState,
    val chrome: ViewerChromeState,
    val search: ViewerSearchState,
    val bars: ViewerBarHeights,
    val lastSeen: LastSeen,
    val hazeState: HazeState,
    val selectionDrag: SelectionDrag,
    private val focusManager: FocusManager,
    private val scope: CoroutineScope,
    private val revealMarginPx: Float,
) {
    /**
     * The bars stay while a search is open: stepping between matches scrolls the document, which
     * would otherwise send them away, and they hold the way out of the search.
     */
    val barsVisible: Boolean
        get() = chrome.isVisible || search.isOpen

    val loadError: ViewerLoadError?
        get() = pdf.error?.let(ViewerLoadError::of)

    fun dismissKeyboard() = focusManager.clearFocus()

    /** Also shows the bars: reading on before the search may have hidden them. */
    fun closeSearch() {
        focusManager.clearFocus()
        search.close()
        chrome.show()
    }

    fun zoomIn() {
        scope.launch { pdf.animateZoomTo(pdf.zoom * ZoomStep) }
    }

    fun zoomOut() {
        scope.launch { pdf.animateZoomTo(pdf.zoom / ZoomStep) }
    }

    fun resetZoom() {
        scope.launch { pdf.animateResetZoom() }
    }

    fun scrollTo(page: Int, position: Offset?) {
        scope.launch { pdf.animateScrollTo(page, position) }
    }

    /** Launched, so a newer match is not kept waiting behind this one's scroll. */
    fun reveal(page: Int, area: NormalizedRect) {
        scope.launch {
            pdf.reveal(page, area, bars.visibleArea(searching = search.isOpen), revealMarginPx)
        }
    }

    /**
     * The page and point at the top of the content area: where a jump puts its target, so the way
     * back returns the reader to the line they were on.
     */
    fun readingPoint(): Pair<Int, Offset>? = pdf.readingPoint(bars.topPx.toFloat())
}

/**
 * @param documentUri Where the document is read from. Saving another app's document into the
 *   library moves it, and the engine does not take a new state under a viewer already laid out, so
 *   the engine's state is keyed by it and starts where the reader was last seen.
 * @param start Where the document opens the first time. What was last seen wins over it: after a
 *   rotation or a process death it is the later of the two.
 */
@Composable
internal fun rememberPdfViewerScreenState(
    documentUri: String,
    start: ReadingPosition?,
): PdfViewerScreenState {
    val lastSeen = remember { LastSeen() }
    val pdf =
        key(documentUri) {
            rememberPdfViewerState(initialPosition = lastSeen.position ?: start?.toEngine())
        }
    val chrome = rememberViewerChromeState()
    val search = rememberViewerSearchState()
    val bars = remember { ViewerBarHeights() }
    val hazeState = rememberHazeState()
    val selectionDrag = rememberSelectionDrag(pdf)
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val revealMarginPx = with(LocalDensity.current) { RevealMargin.toPx() }
    return remember(
        pdf,
        chrome,
        search,
        hazeState,
        selectionDrag,
        focusManager,
        scope,
        revealMarginPx,
    ) {
        PdfViewerScreenState(
            pdf = pdf,
            chrome = chrome,
            search = search,
            bars = bars,
            lastSeen = lastSeen,
            hazeState = hazeState,
            selectionDrag = selectionDrag,
            focusManager = focusManager,
            scope = scope,
            revealMarginPx = revealMarginPx,
        )
    }
}

/**
 * The bars' measured heights, kept while they are hidden. They reach the viewer as constant content
 * padding: pages start below the top bar, end above the bottom one, and scroll underneath them. An
 * animated padding resized the viewport, and relaid the document out, on every frame the bars
 * moved.
 */
@Stable
internal class ViewerBarHeights {
    var topPx by mutableIntStateOf(0)
        private set

    var bottomPx by mutableIntStateOf(0)
        private set

    // Plain values: they are read to bring a match into view, never to draw.
    private var viewerSize = IntSize.Zero
    private var findToolbarPx = 0

    fun onViewerMeasured(size: IntSize) {
        viewerSize = size
    }

    fun onTopBarMeasured(heightPx: Int) {
        topPx = heightPx
    }

    fun onBottomBarMeasured(heightPx: Int) {
        bottomPx = heightPx
    }

    fun onFindToolbarMeasured(heightPx: Int) {
        findToolbarPx = heightPx
    }

    /**
     * Where something on the pages can be seen: below the top bar and above whatever covers the
     * bottom. While searching that is the find toolbar, which rises with the keyboard and so
     * includes it.
     */
    fun visibleArea(searching: Boolean): VisibleArea {
        val covered = if (searching) maxOf(bottomPx, findToolbarPx) else bottomPx
        return VisibleArea(
            top = topPx.toFloat(),
            bottom = (viewerSize.height - covered).toFloat(),
            right = viewerSize.width.toFloat(),
        )
    }
}

/** In the viewer's pixels. */
internal class VisibleArea(val top: Float, val bottom: Float, val right: Float)

/** Where the reader was last seen, and the last place the ViewModel was told about. */
internal class LastSeen {
    var position: PdfReadingPosition? = null
    var reported: PdfReadingPosition? = null
}

/**
 * Brings the match at [area] of [page] into [visible], unless it is already there: stepping between
 * matches on one screen should not make the reader lose their place. A match out of view lands
 * about a third of the way down, with some of the text before it to read it by.
 *
 * @param margin How close to an edge of [visible] a match may be and still count as in view.
 */
private suspend fun PdfViewerState.reveal(
    page: Int,
    area: NormalizedRect,
    visible: VisibleArea,
    margin: Float,
) {
    val bounds = pageRectInViewer(page) ?: return
    val top = bounds.top + area.top * bounds.height
    val bottom = bounds.top + area.bottom * bounds.height
    val left = bounds.left + area.left * bounds.width
    val right = bounds.left + area.right * bounds.width

    val inView =
        top >= visible.top + margin &&
            bottom <= visible.bottom - margin &&
            left >= 0f &&
            right <= visible.right
    if (inView) return

    // The scroll puts its point at the top of the content area: aim that far above the match.
    val lift = (visible.bottom - visible.top) * RevealPlacement / bounds.height
    animateScrollTo(page, Offset((area.left + area.right) / 2f, area.top - lift))
}

private fun PdfViewerState.readingPoint(contentTop: Float): Pair<Int, Offset>? {
    val y = contentTop + 1f
    val page =
        visiblePages.firstOrNull { index ->
            pageRectInViewer(index)?.let { y <= it.bottom } == true
        } ?: return null
    val rect = pageRectInViewer(page) ?: return null
    // Between two pages, the top of the next one.
    val hit = hitTest(Offset(rect.center.x, maxOf(rect.top + 1f, y)))
    val at = hit.pagePosition ?: return null
    return (hit.pageIndex ?: page) to at
}

private fun ReadingPosition.toEngine() =
    PdfReadingPosition(pageIndex = pageIndex, fraction = offset)

private const val ZoomStep = 1.25f

/** Where a match is put when it is brought into view: this share of the way down the free area. */
private const val RevealPlacement = 0.3f

/** How close to the bars or the keyboard a match may be and still count as in view. */
private val RevealMargin = 24.dp
