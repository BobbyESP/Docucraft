/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.selection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.PagePoint
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.PageTextState
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.SelectionInteraction
import com.bobbyesp.documentcontent.DocumentSelection
import com.bobbyesp.documentcontent.NormalizedPoint
import com.bobbyesp.documentcontent.TextCaret
import com.composepdf.PdfOverlayScope
import com.composepdf.PdfViewerState
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * A selection being dragged, by the finger of a long press or by a handle: one end stays where it
 * is (the anchor) and the other follows a point on screen. Near the top or bottom of the content
 * area the document scrolls under that point, which is how a selection runs onto pages that were
 * not on screen, and the selection follows it there even while the finger holds still.
 */
@Stable
class SelectionDrag
internal constructor(
    private val state: PdfViewerState,
    private val scope: CoroutineScope,
    private val haptics: HapticFeedback,
) {
    /** Whether a drag is under way; the toolbar keeps out of the way meanwhile. */
    var isActive by mutableStateOf(false)
        private set

    internal var pages: () -> Map<Int, PageTextState> = { emptyMap() }
    internal var onSelect: (DocumentSelection) -> Unit = {}

    /** The content area's top and bottom, in viewer pixels, and how fast to scroll at its edges. */
    internal var contentTop = 0f
    internal var contentBottom = 0f
    internal var edgeZone = 0f
    internal var maxSpeed = 0f

    private var anchor: DocumentSelection? = null
    private var point = Offset.Zero
    private var last: DocumentSelection? = null
    private var autoScroll: Job? = null

    /**
     * Starts growing [anchor] towards [at], in viewer pixels, from [current]. The anchor stays
     * selected: the word a long press chose, or the caret at the end a handle does not move.
     */
    fun start(anchor: DocumentSelection, current: DocumentSelection, at: Offset) {
        this.anchor = anchor
        last = current
        point = at
        isActive = true
        autoScroll?.cancel()
        autoScroll = scope.launch { scrollAtEdges() }
    }

    fun moveTo(at: Offset) {
        point = at
        follow()
    }

    fun end() {
        isActive = false
        anchor = null
        autoScroll?.cancel()
        autoScroll = null
    }

    private fun follow() {
        val from = anchor ?: return
        val hit = state.hitTest(point)
        val page = hit.pageIndex ?: return
        val position = hit.pagePosition ?: return
        val next =
            SelectionInteraction.extend(
                from,
                pages(),
                page,
                NormalizedPoint(position.x, position.y),
            ) ?: return
        if (next == last) return
        last = next
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onSelect(next)
    }

    private suspend fun scrollAtEdges() {
        var previous = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val seconds = (now - previous) / 1_000_000_000f
            previous = now
            val speed = edgeSpeed(point.y)
            if (speed != 0f && state.panBy(Offset(0f, speed * seconds)) != Offset.Zero) follow()
        }
    }

    /**
     * Pixels a second to move the document by: faster the deeper into an edge zone. Near the top
     * the document moves down, bringing earlier text in; near the bottom, up.
     */
    private fun edgeSpeed(y: Float): Float {
        if (edgeZone <= 0f || contentBottom <= contentTop) return 0f
        val intoTop = (contentTop + edgeZone - y) / edgeZone
        val intoBottom = (y - (contentBottom - edgeZone)) / edgeZone
        return when {
            intoTop > 0f -> intoTop.coerceAtMost(1f) * maxSpeed
            intoBottom > 0f -> -intoBottom.coerceAtMost(1f) * maxSpeed
            else -> 0f
        }
    }
}

@Composable
fun rememberSelectionDrag(state: PdfViewerState): SelectionDrag {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    return remember(state, scope, haptics) { SelectionDrag(state, scope, haptics) }
}

/**
 * Everything text selection shows over the pages: the highlight, the two handles, the system's text
 * toolbar (Copy, Select all), and what TalkBack announces. It follows the document through the
 * overlay's page coordinates, so panning and zooming never recompose it.
 *
 * @param contentTop Where the content area starts, below the top bar.
 * @param contentBottomInset How much of the viewer's bottom the bottom bar covers.
 * @param nightMode Whether the pages are drawn inverted, dark: the usual selection color, made for
 *   light text fields, hardly shows on them.
 */
@Composable
fun PdfOverlayScope.TextSelectionLayer(
    state: PdfViewerState,
    pages: Map<Int, PageTextState>,
    selection: DocumentSelection?,
    drag: SelectionDrag,
    contentTop: Dp,
    contentBottomInset: Dp,
    nightMode: Boolean,
    onSelect: (DocumentSelection) -> Unit,
    onCopy: () -> Unit,
    onSelectAll: () -> Unit,
) {
    val latestPages by rememberUpdatedState(pages)
    val latestOnSelect by rememberUpdatedState(onSelect)
    val density = LocalDensity.current
    var viewer by remember { mutableStateOf<LayoutCoordinates?>(null) }

    drag.pages = { latestPages }
    drag.onSelect = { latestOnSelect(it) }
    with(density) {
        drag.contentTop = contentTop.toPx()
        drag.edgeZone = EdgeZone.toPx()
        drag.maxSpeed = EdgeScrollSpeed.toPx()
    }
    val bottomInsetPx = with(density) { contentBottomInset.toPx() }

    // The viewer's own coordinates: where handles report the finger, and where the toolbar is
    // placed from.
    Spacer(
        Modifier.matchParentSize().onPlaced {
            viewer = it
            drag.contentBottom = it.size.height - bottomInsetPx
        }
    )

    if (selection == null) return

    val highlight =
        if (nightMode) MaterialTheme.colorScheme.inversePrimary.copy(alpha = NightHighlightAlpha)
        else LocalTextSelectionColors.current.backgroundColor
    DrawOnPages {
        val text = pages[pageIndex] as? PageTextState.Text ?: return@DrawOnPages
        for (rect in selection.highlightRects(pageIndex, text.selection)) {
            val area = toViewer(Rect(rect.left, rect.top, rect.right, rect.bottom))
            drawRect(color = highlight, topLeft = area.topLeft, size = area.size)
        }
    }

    val handles = SelectionInteraction.handles(selection, pages)
    handles.start?.let { at ->
        SelectionHandle(
            isStart = true,
            at = at,
            label = stringResource(R.string.viewer_selection_start_handle),
            viewer = { viewer },
            onDragStart = { finger -> drag.start(caret(selection.end), selection, finger) },
            onDrag = drag::moveTo,
            onDragEnd = drag::end,
        )
    }
    handles.end?.let { at ->
        SelectionHandle(
            isStart = false,
            at = at,
            label = stringResource(R.string.viewer_selection_end_handle),
            viewer = { viewer },
            onDragStart = { finger -> drag.start(caret(selection.start), selection, finger) },
            onDrag = drag::moveTo,
            onDragEnd = drag::end,
        )
    }

    SelectionToolbar(
        state = state,
        selection = selection,
        pages = pages,
        dragging = drag.isActive,
        viewer = { viewer },
        onCopy = onCopy,
        onSelectAll = onSelectAll,
    )

    // What TalkBack says once the selection settles.
    if (!drag.isActive) {
        val selected = selection.text { (pages[it] as? PageTextState.Text)?.selection }
        val announcement =
            stringResource(R.string.viewer_selection_announcement, selected.take(200))
        Box(
            Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = announcement
            }
        )
    }
}

/**
 * A selection handle: a drop whose point sits on the text, hanging below it, inside a 48 dp touch
 * target. Dragging reports the finger in the viewer's coordinates, which stay right while the
 * handle moves under the finger; the offset from the finger to the text is kept, so the text point
 * does not jump to where the finger is.
 */
@Composable
private fun PdfOverlayScope.SelectionHandle(
    isStart: Boolean,
    at: PagePoint,
    label: String,
    viewer: () -> LayoutCoordinates?,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val color = LocalTextSelectionColors.current.handleColor
    var self by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // The gesture outlives recompositions: it must call the latest callbacks, which know the
    // latest selection.
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnDrag by rememberUpdatedState(onDrag)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)
    val density = LocalDensity.current

    Canvas(
        modifier =
            Modifier.anchorTo(
                    pageIndex = at.page,
                    position = Offset(at.position.x, at.position.y),
                    alignment = if (isStart) StartHandlePoint else EndHandlePoint,
                )
                .size(TouchTarget)
                .onPlaced { self = it }
                .semantics { contentDescription = label }
                .pointerInput(Unit) {
                    fun inViewer(local: Offset): Offset? {
                        val viewerCoordinates = viewer() ?: return null
                        val handle = self ?: return null
                        return viewerCoordinates.localPositionOf(handle, local)
                    }
                    awaitEachGesture {
                        // Taken at once, with no slop: a handle exists to be dragged, and the
                        // viewer yields a touch whose down was consumed (E2).
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val finger = inViewer(down.position) ?: return@awaitEachGesture
                        // The handle's point is its top centre; aim a little above it, inside
                        // the line, so the nearest line is the right one. The offset is taken
                        // from the first touch, so the text point moves exactly as the finger.
                        val tip =
                            inViewer(Offset(size.width * tipFraction(isStart), 0f))
                                ?: return@awaitEachGesture
                        val grab = tip - finger - Offset(0f, with(density) { AimAbove.toPx() })
                        var dragging = false
                        try {
                            drag(down.id) { change ->
                                if (!dragging) {
                                    dragging = true
                                    latestOnDragStart(finger + grab)
                                }
                                change.consume()
                                inViewer(change.position)?.let { latestOnDrag(it + grab) }
                            }
                        } finally {
                            if (dragging) latestOnDragEnd()
                        }
                    }
                }
    ) {
        val radius = DropRadius.toPx()
        val centreX = size.width * tipFraction(isStart)
        val circle = if (isStart) centreX - radius else centreX + radius
        drawCircle(color = color, radius = radius, center = Offset(circle, radius))
        // The square quarter that turns the circle into a drop pointing at the text.
        drawRect(
            color = color,
            topLeft = Offset(if (isStart) centreX - radius else centreX, 0f),
            size = Size(radius, radius),
        )
    }
}

/**
 * The system's text toolbar over the selection. Hidden while dragging and while the document moves,
 * shown again once it settles, over the part of the selection that is on screen.
 */
@Composable
private fun SelectionToolbar(
    state: PdfViewerState,
    selection: DocumentSelection,
    pages: Map<Int, PageTextState>,
    dragging: Boolean,
    viewer: () -> LayoutCoordinates?,
    onCopy: () -> Unit,
    onSelectAll: () -> Unit,
) {
    val toolbar = LocalTextToolbar.current
    val latestPages by rememberUpdatedState(pages)
    val latestOnCopy by rememberUpdatedState(onCopy)
    val latestOnSelectAll by rememberUpdatedState(onSelectAll)

    LaunchedEffect(selection, dragging) {
        if (dragging) {
            toolbar.hide()
            return@LaunchedEffect
        }
        snapshotFlow { Triple(state.panX, state.panY, state.zoom) }
            .collectLatest {
                toolbar.hide()
                delay(ToolbarSettleDelay)
                val area = selectionInRoot(state, selection, latestPages, viewer())
                if (area == null) {
                    toolbar.hide()
                } else {
                    toolbar.showMenu(
                        rect = area,
                        onCopyRequested = { latestOnCopy() },
                        onSelectAllRequested = { latestOnSelectAll() },
                    )
                }
            }
    }
    DisposableEffect(toolbar) { onDispose { toolbar.hide() } }
}

/** The on-screen part of [selection], in root coordinates; `null` when none of it is on screen. */
private fun selectionInRoot(
    state: PdfViewerState,
    selection: DocumentSelection,
    pages: Map<Int, PageTextState>,
    viewer: LayoutCoordinates?,
): Rect? {
    if (viewer == null || !viewer.isAttached) return null
    val screen = Rect(Offset.Zero, Size(viewer.size.width.toFloat(), viewer.size.height.toFloat()))
    var union: Rect? = null
    for (page in state.visiblePages) {
        val text = pages[page] as? PageTextState.Text ?: continue
        val bounds = state.pageRectInViewer(page) ?: continue
        for (rect in selection.highlightRects(page, text.selection)) {
            val area =
                Rect(
                    bounds.left + rect.left * bounds.width,
                    bounds.top + rect.top * bounds.height,
                    bounds.left + rect.right * bounds.width,
                    bounds.top + rect.bottom * bounds.height,
                )
            if (!area.overlaps(screen)) continue
            union =
                union?.let {
                    Rect(
                        minOf(it.left, area.left),
                        minOf(it.top, area.top),
                        maxOf(it.right, area.right),
                        maxOf(it.bottom, area.bottom),
                    )
                } ?: area
        }
    }
    val visible = union?.intersect(screen) ?: return null
    return Rect(viewer.localToRoot(visible.topLeft), viewer.localToRoot(visible.bottomRight))
}

/**
 * Where along its touch target a handle's point is. Each handle hangs outward, the start one to the
 * left of its point and the end one to the right, as in the reference viewers, so that on a short
 * selection their targets do not sit on top of each other and a touch reaches the one it meant.
 */
private fun tipFraction(isStart: Boolean) = if (isStart) 0.75f else 0.25f

/** The alignment point matching [tipFraction], at the top: where a handle meets its text. */
private val StartHandlePoint = BiasAlignment(horizontalBias = 0.5f, verticalBias = -1f)
private val EndHandlePoint = BiasAlignment(horizontalBias = -0.5f, verticalBias = -1f)

/** A selection of nothing at [at]: what a handle grows from, the end it does not move. */
private fun caret(at: TextCaret) = DocumentSelection(at, at)

private const val NightHighlightAlpha = 0.45f

private val TouchTarget = 48.dp
private val DropRadius = 11.dp
private val AimAbove = 4.dp
private val EdgeZone = 56.dp

/** Per second, at the very edge. */
private val EdgeScrollSpeed = 1200.dp

private val ToolbarSettleDelay = 300.milliseconds
