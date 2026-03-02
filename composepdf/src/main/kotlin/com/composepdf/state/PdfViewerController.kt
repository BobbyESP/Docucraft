package com.composepdf.state

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.composepdf.cache.BitmapCache
import com.composepdf.cache.BitmapPool
import com.composepdf.remote.RemotePdfLoader
import com.composepdf.remote.RemotePdfState
import com.composepdf.renderer.PageRenderer
import com.composepdf.renderer.PdfDocumentManager
import com.composepdf.renderer.RenderScheduler
import com.composepdf.source.PdfSource
import com.composepdf.state.PdfViewerController.Companion.RENDER_DEBOUNCE_MS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.Closeable

private const val TAG = "PdfViewerController"

/**
 * Brain of the PDF viewer — bridges [PdfViewerState] with the rendering pipeline.
 *
 * ## Responsibilities
 *
 * - **Document lifecycle**: opens/closes [PdfDocumentManager], loads page sizes.
 * - **Geometry**: converts between document space (zoom = 1) and screen pixels.
 * - **Gesture handling**: applies pinch-zoom, pan, and double-tap maths to [PdfViewerState].
 * - **Pan clamping**: keeps the document within scroll bounds after every transformation.
 * - **Render scheduling**: decides which pages need rendering and at what quality,
 *   then delegates to [RenderScheduler].
 * - **Re-render debounce**: after a zoom gesture ends, waits [RENDER_DEBOUNCE_MS] before
 *   requesting a fresh high-quality render so we don't saturate the renderer mid-pinch.
 *
 * ## Coordinate system
 *
 * ```
 * screenX = panX                       // panX = left edge of every page
 * screenY = pageTopDocY(i) × zoom + panY
 * ```
 *
 * At zoom = 1, [clampPan] sets `panX = (vpW − scaledW) / 2` to horizontally centre
 * the page. When zoomed in, `panX ∈ [−(scaledW − vpW), 0]` gives the full scroll range.
 *
 * ## Lifecycle
 *
 * The controller is created inside [com.composepdf.PdfViewer] via `remember` and released via
 * [androidx.compose.runtime.DisposableEffect] → [close]. Do not share a controller across multiple [PdfViewer]
 * instances; each viewer owns its own controller.
 */
@Stable
class PdfViewerController(
    private val context: Context,
    private val state: PdfViewerState,
    private val config: ViewerConfig
) : Closeable {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val bitmapPool = BitmapPool()
    private val bitmapCache = BitmapCache(bitmapPool = bitmapPool)
    private val documentManager = PdfDocumentManager(context)
    private val pageRenderer = PageRenderer(bitmapPool)
    private val renderScheduler = RenderScheduler(documentManager, pageRenderer, bitmapCache)

    val renderedPages: StateFlow<Map<Int, Bitmap>> = renderScheduler.renderedPages

    /**
     * Signals that zoom changed and a quality re-render should be triggered.
     * CONFLATED so rapid pinch frames collapse to a single pending signal.
     */
    private val reRenderSignal = Channel<Unit>(capacity = Channel.CONFLATED)

    init {
        renderScheduler.prefetchWindow = config.prefetchDistance
        Log.d(
            TAG,
            "init prefetchWindow=${config.prefetchDistance} renderQuality=${config.renderQuality} min=${config.minZoom} max=${config.maxZoom}"
        )

        // After a zoom gesture ends, wait briefly then request a fresh render at the new zoom.
        // CONFLATED channel: rapid onGestureEnd calls collapse to one pending signal.
        scope.launch(Dispatchers.Main.immediate) {
            reRenderSignal.consumeEach {
                delay(RENDER_DEBOUNCE_MS)
                Log.d(TAG, "debounce elapsed — requesting re-render at zoom=${state.zoom}")
                requestRenderForVisiblePages()
            }
        }
    }

    // ── Viewport ──────────────────────────────────────────────────────────────

    /** Current viewport width in physical screen pixels. Updated by [onViewportSizeChanged]. */
    var viewportWidth: Float by mutableFloatStateOf(0f)
        private set

    /** Current viewport height in physical screen pixels. Updated by [onViewportSizeChanged]. */
    var viewportHeight: Float by mutableFloatStateOf(0f)
        private set

    /**
     * Original page sizes reported by [android.graphics.pdf.PdfRenderer] in PDF points
     * (1 pt = 1/72 inch). These never change after the document is opened; they define
     * the aspect ratio of each page. Actual on-screen pixel dimensions are derived by
     * scaling these against [viewportWidth] and [state.zoom].
     */
    var pageSizes: List<Size> by mutableStateOf(emptyList())
        private set

    /**
     * Called by [PdfLayout] via [Modifier.onSizeChanged] whenever the composable is
     * first measured or the device is rotated. Re-clamps pan (so the document stays
     * in-bounds after the viewport resizes) and triggers a fresh render.
     */
    fun onViewportSizeChanged(width: Float, height: Float) {
        if (width == viewportWidth && height == viewportHeight) return
        viewportWidth = width
        viewportHeight = height
        clampPan()
        requestRenderForVisiblePages()
    }

    // ── Document loading ──────────────────────────────────────────────────────

    /**
     * Begins loading [source] asynchronously.
     *
     * Resets [PdfViewerState] to the loading state before opening so that the
     * UI shows a progress indicator immediately even if a previous document was shown.
     * For remote sources, delegates to [RemotePdfLoader] which emits download progress
     * via [PdfViewerState.remoteState].
     */
    fun loadDocument(source: PdfSource) {
        scope.launch {
            state.reset()
            try {
                when (source) {
                    is PdfSource.Remote -> loadRemoteDocument(source)
                    else -> openDocument(source)
                }
            } catch (e: Exception) {
                state.error = e
                state.isLoading = false
            }
        }
    }

    private suspend fun openDocument(source: PdfSource) {
        documentManager.open(source)
        pageSizes = documentManager.getAllPageSizes()
        state.pageCount = documentManager.pageCount
        state.isLoading = false
        clampPan()
        requestRenderForVisiblePages()
    }

    private suspend fun loadRemoteDocument(source: PdfSource.Remote) {
        RemotePdfLoader(context).load(source).collect { remoteState ->
            state.remoteState = remoteState
            when (remoteState) {
                is RemotePdfState.Cached -> openDocument(PdfSource.File(remoteState.file))
                is RemotePdfState.Error -> {
                    state.error = com.composepdf.remote.RemotePdfException(
                        remoteState.type, remoteState.message, remoteState.cause
                    )
                    state.isLoading = false
                }

                else -> Unit
            }
        }
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────

    /**
     * Returns the height of page [index] in **document space** (unzoomed screen pixels).
     *
     * The width of every page in document space is always [viewportWidth] (fit-width layout).
     * Height is computed by preserving the page's aspect ratio from the PDF point dimensions.
     */
    fun pageHeightPx(index: Int): Float {
        val s = pageSizes.getOrNull(index) ?: return viewportWidth
        return viewportWidth * s.height.toFloat() / s.width.toFloat()
    }

    /**
     * Returns the Y coordinate of the **top edge** of page [index] in document space
     * (unzoomed, before pan). This is the sum of heights and spacings of all preceding pages.
     *
     * To convert to a screen Y: `screenY = pageTopDocY(index) × zoom + panY`.
     */
    fun pageTopDocY(index: Int): Float {
        var y = 0f
        repeat(index) { i -> y += pageHeightPx(i) + config.pageSpacingPx }
        return y
    }

    /**
     * Total height of all pages plus inter-page spacing in document space.
     * The trailing spacing after the last page is excluded so the document
     * does not have extra blank space at the bottom.
     */
    private val totalDocumentHeight: Float
        get() {
            if (pageSizes.isEmpty()) return 0f
            var h = 0f
            pageSizes.indices.forEach { i -> h += pageHeightPx(i) + config.pageSpacingPx }
            return (h - config.pageSpacingPx).coerceAtLeast(0f)
        }

    // ── Visibility ────────────────────────────────────────────────────────────

    /**
     * Returns the range of page indices that are at least partially visible in the viewport.
     *
     * Converts the current viewport edges to document space, then iterates through all pages
     * checking intersection. A small [margin] (half the page spacing) is added on each side
     * so pages start rendering slightly before they scroll into view, preventing pop-in.
     *
     * Called on every recomposition frame from [PdfLayout] via [derivedStateOf], so it must
     * be fast (O(n) loop with no allocations beyond the IntRange return value).
     */
    fun visiblePageIndices(): IntRange {
        if (pageSizes.isEmpty()) return 0..0
        if (viewportHeight == 0f) return pageSizes.indices

        val spacingPx = config.pageSpacingPx
        // Half-spacing margin: pages begin rendering just before they enter the viewport.
        val margin = spacingPx * 0.5f
        // Convert viewport top/bottom edges from screen to document space.
        val docTop = (-state.panY / state.zoom) - margin
        val docBottom = ((viewportHeight - state.panY) / state.zoom) + margin

        var first = pageSizes.lastIndex
        var last = 0
        var y = 0f

        for (i in pageSizes.indices) {
            val h = pageHeightPx(i)
            val pageTop = y
            val pageBottom = y + h
            if (pageBottom >= docTop && pageTop <= docBottom) {
                if (i < first) first = i
                if (i > last) last = i
            }
            y += h + spacingPx
        }

        if (first > last) return pageSizes.indices
        return first..last
    }

    /**
     * Returns the index of the page whose centre is closest to the vertical centre of
     * the viewport. Used to update [PdfViewerState.currentPage] after a scroll ends.
     */
    fun currentPageFromPan(): Int {
        if (pageSizes.isEmpty()) return 0
        val vpCenterDoc = (viewportHeight / 2f - state.panY) / state.zoom
        var y = 0f
        for (i in pageSizes.indices) {
            val h = pageHeightPx(i)
            if (vpCenterDoc <= y + h) return i
            y += h + config.pageSpacingPx
        }
        return pageSizes.lastIndex
    }

    // ── Gesture callbacks ─────────────────────────────────────────────────────

    fun onGestureStart() {
        Log.d(TAG, "onGestureStart — zoom=${state.zoom}")
        state.isGestureActive = true
    }

    /**
     * Called when all fingers are lifted (end of pan, pinch, or fling animation).
     * Re-clamps pan to valid bounds, updates [PdfViewerState.currentPage], and sends
     * a signal to the debounce channel so a quality re-render is scheduled after
     * [RENDER_DEBOUNCE_MS] of inactivity.
     */
    fun onGestureEnd() {
        state.isGestureActive = false
        clampPan()
        state.currentPage = currentPageFromPan()
        Log.d(TAG, "onGestureEnd — zoom=${state.zoom}  sending reRenderSignal")
        reRenderSignal.trySend(Unit)
    }

    /**
     * Applies one incremental frame of a pinch-zoom + pan gesture.
     *
     * The pivot point (centroid of the two fingers) must stay fixed in document space,
     * so when zoom changes, pan is adjusted by the affine formula:
     * ```
     * newPan = pivot × (1 − ratio) + oldPan × ratio
     * ```
     * This is equivalent to:
     * ```
     * docPoint  = (pivot − oldPan) / oldZoom        // point under pivot, doc space
     * newPan    = pivot − docPoint × newZoom         // keep it fixed at pivot
     * ```
     * After zoom, [panDelta] (finger translation) is applied and [clampPan] enforces bounds.
     */
    fun onGestureUpdate(zoomChange: Float, panDelta: Offset, pivot: Offset) {
        if (viewportWidth == 0f) return

        val oldZoom = state.zoom
        val newZoom = (oldZoom * zoomChange).coerceIn(config.minZoom, config.maxZoom)

        if (newZoom != oldZoom) {
            val ratio = newZoom / oldZoom
            state.panX = pivot.x * (1f - ratio) + state.panX * ratio
            state.panY = pivot.y * (1f - ratio) + state.panY * ratio
            state.zoom = newZoom
        }

        state.panX += panDelta.x
        state.panY += panDelta.y
        clampPan()
    }

    /**
     * Applies one frame of the double-tap zoom animation.
     *
     * Unlike [onGestureUpdate] which receives incremental zoom *changes*, this receives
     * the **absolute** target zoom for the current animation frame. Using absolute values
     * avoids floating-point drift that accumulates when chaining many small multiplications.
     */
    fun onAnimatedZoomFrame(targetZoom: Float, pivot: Offset) {
        if (viewportWidth == 0f) return
        val oldZoom = state.zoom
        val newZoom = targetZoom.coerceIn(config.minZoom, config.maxZoom)
        if (newZoom == oldZoom) return

        val ratio = newZoom / oldZoom
        state.panX = pivot.x * (1f - ratio) + state.panX * ratio
        state.panY = pivot.y * (1f - ratio) + state.panY * ratio
        state.zoom = newZoom
        clampPan()
    }

    /**
     * Returns `true` if [screenPoint] falls within the bounds of any page.
     *
     * All pages share the same X bounds (same width). Only the Y range differs
     * per page. Used by the gesture handler to reject double-taps on the grey
     * background between or around pages.
     */
    fun isPointOverPage(screenPoint: Offset): Boolean {
        if (pageSizes.isEmpty()) return false
        val scaledW = viewportWidth * state.zoom
        val left = state.panX
        val right = left + scaledW
        if (screenPoint.x < left || screenPoint.x > right) return false

        var docY = 0f
        for (i in pageSizes.indices) {
            val h = pageHeightPx(i)
            val top = docY * state.zoom + state.panY
            val bottom = top + h * state.zoom
            if (screenPoint.y in top..bottom) return true
            docY += h + config.pageSpacingPx
        }
        return false
    }

    /**
     * Programmatically scrolls to page [index], positioning its top edge at the
     * top of the viewport. Clamps [index] to valid bounds and re-clamps pan
     * so the document stays within scroll limits.
     */
    fun goToPage(index: Int) {
        if (pageSizes.isEmpty()) return
        val i = index.coerceIn(0, pageSizes.lastIndex)
        state.panY = -(pageTopDocY(i) * state.zoom)
        state.currentPage = i
        clampPan()
        requestRenderForVisiblePages()
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    /**
     * Requests renders for all currently visible pages at the current zoom.
     *
     * The [RenderScheduler] deduplicates:
     * - Same zoom + in-flight job → skip (already coming).
     * - Same zoom + cache hit    → publish immediately, skip job.
     * - Different zoom           → cancel stale job, launch new one.
     *
     * So this method is safe to call frequently (scroll, zoom change, layout change).
     */
    fun requestRenderForVisiblePages() {
        if (!documentManager.isOpen || pageSizes.isEmpty() || viewportWidth == 0f) return
        val visible = visiblePageIndices()
        val zoom = state.zoom
        val vpWidth = viewportWidth
        Log.d(TAG, "requestRender zoom=$zoom vpW=$vpWidth visible=$visible")
        scope.launch(Dispatchers.IO) {
            renderScheduler.requestRender(
                visiblePages = visible,
                config = PageRenderer.RenderConfig(
                    zoomLevel = zoom,
                    renderQuality = config.renderQuality,
                    viewportWidthPx = vpWidth,
                    nightMode = config.isNightModeEnabled
                )
            )
        }
    }

    // ── Pan clamping ──────────────────────────────────────────────────────────

    /**
     * Constrains [PdfViewerState.panX] and [PdfViewerState.panY] so the document
     * always stays within reachable scroll bounds.
     *
     * ### X axis
     * `panX` is the screen X of the **left edge** of every page (all pages share the same width).
     * ```
     * zoom = 1  →  scaledW == vpW  →  panX = 0  (page exactly fills viewport)
     * zoom > 1  →  panX ∈ [−(scaledW − vpW), 0]
     *               panX = 0           : left edge of page at viewport left
     *               panX = −(scaledW−vpW) : right edge of page at viewport right
     * ```
     * When the page is narrower than the viewport (possible with mixed-size PDFs at zoom < 1),
     * the page is centred: `panX = (vpW − scaledW) / 2`.
     *
     * ### Y axis
     * `panY = 0` means the top of the document is at the top of the viewport.
     * Valid range: `[vpH − scaledDocH, 0]` (scroll until the last page bottom reaches vpH).
     * Short documents (shorter than the viewport) are centred vertically.
     */
    private fun clampPan() {
        if (viewportWidth == 0f || viewportHeight == 0f) return

        val scaledW = viewportWidth * state.zoom
        state.panX = if (scaledW <= viewportWidth) {
            // Content narrower than viewport — centre horizontally.
            (viewportWidth - scaledW) / 2f
        } else {
            state.panX.coerceIn(-(scaledW - viewportWidth), 0f)
        }

        val docH = totalDocumentHeight
        if (docH == 0f) return
        val scaledH = docH * state.zoom
        state.panY = if (scaledH <= viewportHeight) {
            // Content shorter than viewport — centre vertically.
            (viewportHeight - scaledH) / 2f
        } else {
            state.panY.coerceIn(viewportHeight - scaledH, 0f)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun close() {
        scope.cancel()
        reRenderSignal.close()
        renderScheduler.close()
        documentManager.close()
        bitmapCache.clear()
    }

    companion object {
        /** Milliseconds to wait after the last zoom change before re-rendering. */
        private const val RENDER_DEBOUNCE_MS = 150L
    }
}
