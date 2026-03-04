package com.composepdf.state

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import com.composepdf.cache.BitmapCache
import com.composepdf.cache.BitmapPool
import com.composepdf.remote.RemotePdfLoader
import com.composepdf.remote.RemotePdfState
import com.composepdf.renderer.PageRenderer
import com.composepdf.renderer.PdfDocumentManager
import com.composepdf.renderer.RenderScheduler
import com.composepdf.source.PdfSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable

private const val TAG = "PdfViewerController"

/**
 * Central logic engine for the PDF viewer.
 *
 * ## Responsibilities
 *
 * - **Document lifecycle**: opens/closes the PDF via [PdfDocumentManager].
 * - **Layout geometry**: pre-computes page tops and heights at zoom = 1 so that
 *   all coordinate conversions are O(1) look-ups instead of O(n) sums.
 * - **Viewport management**: tracks the physical screen size and recalculates
 *   geometry whenever it changes (e.g. rotation, multi-window resize).
 * - **Gesture handling**: applies zoom and pan deltas, clamps them to valid ranges,
 *   and delegates to [RenderScheduler] for re-renders.
 * - **Render orchestration**: decides which pages are visible and calls
 *   [RenderScheduler.requestRender] with the correct [PageRenderer.RenderConfig].
 *
 * ## Threading model
 *
 * The controller's [scope] runs on [Dispatchers.Main.immediate]. All state mutations
 * (zoom, pan, page sizes…) and scheduler calls happen on the main thread. Heavy work
 * (PDF I/O, bitmap rendering) is dispatched to [Dispatchers.IO] inside [PdfDocumentManager]
 * and [RenderScheduler].
 *
 * @param context  Android [Context] needed for file/asset resolution.
 * @param state    Hoisted UI state — every write triggers Compose recomposition.
 * @param config   Immutable viewer configuration (zoom limits, quality, spacing…).
 */
@Stable
class PdfViewerController(
    private val context: Context,
    private val state: PdfViewerState,
    private val config: ViewerConfig
) : Closeable {

    /**
     * All coroutines run on Main.immediate so that:
     * 1. State writes are always on the main thread → no Compose thread-safety issues.
     * 2. [RenderScheduler]'s job-map is accessed from a single thread → no mutex needed.
     */
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val bitmapPool    = BitmapPool()
    private val bitmapCache   = BitmapCache(bitmapPool = bitmapPool)
    private val documentManager = PdfDocumentManager(context)
    private val pageRenderer  = PageRenderer(bitmapPool)
    private val renderScheduler = RenderScheduler(documentManager, pageRenderer, bitmapCache)

    /** Rendered bitmaps keyed by page index. Observed by [com.composepdf.PdfViewer]. */
    val renderedPages: StateFlow<Map<Int, Bitmap>> = renderScheduler.renderedPages

    init {
        renderScheduler.prefetchWindow = config.prefetchDistance
    }

    // ─────────────────────────────────────────────────────────────
    // Viewport
    // ─────────────────────────────────────────────────────────────

    /** Physical viewport width in pixels. Observed by [com.composepdf.ui.PdfLayout]. */
    var viewportWidth by mutableFloatStateOf(0f)
        private set

    /** Physical viewport height in pixels. */
    var viewportHeight by mutableFloatStateOf(0f)
        private set

    /**
     * Called by [com.composepdf.ui.PdfLayout] via [androidx.compose.ui.layout.onSizeChanged]
     * whenever the viewport dimensions change. Rebuilds layout geometry and re-renders.
     */
    fun onViewportSizeChanged(width: Float, height: Float) {
        if (width == viewportWidth && height == viewportHeight) return
        Log.d(TAG, "onViewportSizeChanged: ${width}×${height}  pageSizes=${pageSizes.size}")
        viewportWidth  = width
        viewportHeight = height
        rebuildPageLayoutCache()
        Log.d(TAG, "onViewportSizeChanged: pageTops.size=${pageTops.size} totalDocHeight=$totalDocHeight")
        clampPan()
        requestRenderForVisiblePages()
    }

    // ─────────────────────────────────────────────────────────────
    // Document
    // ─────────────────────────────────────────────────────────────

    /**
     * Cached page dimensions in PDF points (1 pt = 1/72 inch).
     * Populated once after the document opens; never changes for the same document.
     */
    var pageSizes: List<Size> by mutableStateOf(emptyList())
        private set

    /**
     * Pre-computed Y coordinate (in document space, zoom = 1) of the top of each page.
     * Index i → top of page i in pixels at zoom = 1.
     */
    private var pageTops    = FloatArray(0)

    /**
     * Pre-computed height (in document space, zoom = 1) of each page in pixels.
     * Height is derived from the page's aspect ratio scaled to [viewportWidth].
     */
    private var pageHeights = FloatArray(0)

    /**
     * Total height of all pages + spacing at zoom = 1, in pixels.
     * Used by [clampPan] to compute the vertical scroll limit.
     */
    private var totalDocHeight = 0f

    /**
     * Rebuilds [pageTops], [pageHeights], and [totalDocHeight] from the current
     * [pageSizes] and [viewportWidth].
     *
     * Must be called whenever [pageSizes] or [viewportWidth] changes.
     * O(n) in page count; typically a few microseconds.
     */
    private fun rebuildPageLayoutCache() {
        if (pageSizes.isEmpty() || viewportWidth == 0f) {
            pageTops    = FloatArray(0)
            pageHeights = FloatArray(0)
            totalDocHeight = 0f
            return
        }

        val count   = pageSizes.size
        val spacing = config.pageSpacingPx

        pageTops    = FloatArray(count)
        pageHeights = FloatArray(count)

        var y = 0f
        for (i in 0 until count) {
            val s = pageSizes[i]
            // Scale the PDF page (in points) to fill [viewportWidth] horizontally.
            val h = viewportWidth * s.height.toFloat() / s.width.toFloat()
            pageTops[i]    = y
            pageHeights[i] = h
            y += h + spacing
        }

        totalDocHeight = (y - spacing).coerceAtLeast(0f)
    }

    // ─────────────────────────────────────────────────────────────
    // Document loading
    // ─────────────────────────────────────────────────────────────

    /**
     * Opens [source] and initialises the viewer. Handles remote sources by
     * downloading/caching the file first via [RemotePdfLoader].
     */
    fun loadDocument(source: PdfSource) {
        scope.launch {
            state.reset()
            try {
                when (source) {
                    is PdfSource.Remote -> loadRemote(source)
                    else                -> open(source)
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadDocument failed: ${e.message}", e)
                state.error     = e
                state.isLoading = false
            }
        }
    }

    /**
     * Opens a local (or asset/uri/stream) PDF source, populates [pageSizes], and
     * triggers the first render.
     *
     * Note: [documentManager.open] and [documentManager.getAllPageSizes] are already
     * suspend functions that dispatch to [Dispatchers.IO] internally via their own
     * mutex. Wrapping them in an extra [withContext] is redundant and can cause
     * nested-dispatcher issues — we call them directly here.
     */
    private suspend fun open(source: PdfSource) {
        Log.d(TAG, "open: opening source=$source")
        documentManager.open(source)
        val sizes = documentManager.getAllPageSizes()
        Log.d(TAG, "open: pageCount=${sizes.size} viewportWidth=$viewportWidth viewportHeight=$viewportHeight")
        pageSizes       = sizes
        state.pageCount = documentManager.pageCount
        state.isLoading = false
        rebuildPageLayoutCache()
        Log.d(TAG, "open: pageTops.size=${pageTops.size} totalDocHeight=$totalDocHeight")
        clampPan()
        requestRenderForVisiblePages()

        // The viewport may not have been measured yet when open() completes
        // (Compose measures layout after the first composition frame).
        // If viewportWidth is still 0, we cannot compute visible pages.
        // onViewportSizeChanged() will fire shortly and trigger a render once the
        // viewport is known — but we also schedule a one-shot retry here to be safe.
        if (viewportWidth == 0f) {
            Log.d(TAG, "open: viewportWidth=0, scheduling retry after first frame")
            scope.launch {
                // Wait two frames for Compose to measure and call onViewportSizeChanged.
                kotlinx.coroutines.delay(32)
                Log.d(TAG, "open retry: viewportWidth=$viewportWidth pageTops.size=${pageTops.size}")
                if (pageTops.isNotEmpty()) requestRenderForVisiblePages()
            }
        }
    }

    /**
     * Downloads / retrieves a remote PDF, then delegates to [open] once cached locally.
     */
    private suspend fun loadRemote(source: PdfSource.Remote) {
        RemotePdfLoader(context).load(source).collect { remote ->
            state.remoteState = remote
            when (remote) {
                is RemotePdfState.Cached -> open(PdfSource.File(remote.file))
                is RemotePdfState.Error  -> {
                    state.error     = Exception(remote.message)
                    state.isLoading = false
                }
                else -> Unit
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Geometry helpers (O(1))
    // ─────────────────────────────────────────────────────────────

    /** Returns the height (in document space, zoom = 1) of page at [index]. */
    fun pageHeightPx(index: Int): Float =
        pageHeights.getOrNull(index) ?: viewportWidth

    /** Returns the Y offset (in document space, zoom = 1) of the top of page [index]. */
    fun pageTopDocY(index: Int): Float =
        pageTops.getOrNull(index) ?: 0f

    // ─────────────────────────────────────────────────────────────
    // Visibility (O(log n) binary search)
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns the range of page indices currently visible in the viewport.
     *
     * Converts screen-space viewport bounds to document space, adds a small margin
     * equal to half the page spacing to avoid a page disappearing one pixel early,
     * then binary-searches [pageTops] for the first and last intersecting pages.
     */
    fun visiblePageIndices(): IntRange {
        if (pageTops.isEmpty() || viewportHeight <= 0f) return IntRange.EMPTY

        val margin    = config.pageSpacingPx * 0.5f
        val docTop    = (-state.panY / state.zoom) - margin
        val docBottom = ((viewportHeight - state.panY) / state.zoom) + margin

        val first = findFirst(docTop)
        val last  = findLast(docBottom)

        return if (first == -1 || last == -1 || first > last) IntRange.EMPTY
        else first..last
    }

    /**
     * Binary search: returns the lowest page index whose **bottom** edge is >= [docTop].
     * Returns -1 if no such page exists (all pages are above the viewport).
     */
    private fun findFirst(docTop: Float): Int {
        var low    = 0
        var high   = pageTops.lastIndex
        var result = -1
        while (low <= high) {
            val mid    = (low + high) ushr 1
            val bottom = pageTops[mid] + pageHeights[mid]
            if (bottom >= docTop) { result = mid; high = mid - 1 } else low = mid + 1
        }
        return result
    }

    /**
     * Binary search: returns the highest page index whose **top** edge is <= [docBottom].
     * Returns -1 if no such page exists (all pages are below the viewport).
     */
    private fun findLast(docBottom: Float): Int {
        var low    = 0
        var high   = pageTops.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (pageTops[mid] <= docBottom) { result = mid; low = mid + 1 } else high = mid - 1
        }
        return result
    }

    /**
     * Returns the page index most visible at the vertical centre of the viewport.
     * Used to update [PdfViewerState.currentPage] after a scroll/fling ends.
     */
    fun currentPageFromPan(): Int {
        if (pageTops.isEmpty()) return 0
        val centerDoc = (viewportHeight / 2f - state.panY) / state.zoom
        return findLast(centerDoc).coerceAtLeast(0)
    }

    /**
     * Returns true if [point] (in screen pixels) is over a rendered page area.
     * Used by the gesture handler to suppress double-tap zoom on the grey background.
     */
    fun isPointOverPage(point: Offset): Boolean {
        if (pageTops.isEmpty()) return false

        val scaledW = viewportWidth * state.zoom
        val left    = state.panX
        val right   = left + scaledW
        if (point.x !in left..right) return false

        val docY  = (point.y - state.panY) / state.zoom
        val index = findLast(docY)
        if (index == -1) return false

        return docY in pageTops[index]..(pageTops[index] + pageHeights[index])
    }

    // ─────────────────────────────────────────────────────────────
    // Gestures
    // ─────────────────────────────────────────────────────────────

    /** Called at the start of every new gesture to mark the state as active. */
    fun onGestureStart() {
        state.isGestureActive = true
    }

    /**
     * Called when all fingers are lifted or a fling animation ends.
     *
     * Clamps pan to valid bounds, updates [PdfViewerState.currentPage], and
     * schedules a re-render at the final zoom level.
     */
    fun onGestureEnd() {
        state.isGestureActive = false
        clampPan()
        state.currentPage = currentPageFromPan()
        // Invalidate cache and re-render at the new zoom.
        renderScheduler.invalidateAll()
        requestRenderForVisiblePages()
    }

    /**
     * Applies an incremental zoom + pan delta from the gesture detector.
     *
     * [zoomChange] is a multiplicative factor (e.g. 1.05 = 5 % zoom-in).
     * [panDelta] is an additive screen-pixel offset.
     * [pivot] is the focal point in screen pixels (centroid of the pinch, or tap position).
     *
     * The zoom is applied around [pivot] so that the content under the user's fingers
     * stays fixed on screen: `newPan = pivot + (oldPan - pivot) * (newZoom / oldZoom)`.
     */
    fun onGestureUpdate(zoomChange: Float, panDelta: Offset, pivot: Offset) {
        if (viewportWidth == 0f) return

        val oldZoom = state.zoom
        val newZoom = (oldZoom * zoomChange).coerceIn(config.minZoom, config.maxZoom)

        if (newZoom != oldZoom) {
            val ratio    = newZoom / oldZoom
            state.panX   = pivot.x + (state.panX - pivot.x) * ratio
            state.panY   = pivot.y + (state.panY - pivot.y) * ratio
            state.zoom   = newZoom
        }

        state.panX += panDelta.x
        state.panY += panDelta.y
        clampPan()
    }

    /**
     * Called on every frame of the double-tap zoom spring animation.
     *
     * Uses absolute zoom values (not incremental) to avoid floating-point drift that
     * accumulates across hundreds of animation frames.
     */
    fun onAnimatedZoomFrame(targetZoom: Float, pivot: Offset) {
        val oldZoom = state.zoom
        val newZoom = targetZoom.coerceIn(config.minZoom, config.maxZoom)
        if (newZoom == oldZoom) return

        val ratio  = newZoom / oldZoom
        state.panX = pivot.x + (state.panX - pivot.x) * ratio
        state.panY = pivot.y + (state.panY - pivot.y) * ratio
        state.zoom = newZoom
        clampPan()
    }

    /**
     * Scrolls the viewport so that page [index] is at the top of the screen.
     */
    fun goToPage(index: Int) {
        if (pageTops.isEmpty()) return
        val i      = index.coerceIn(0, pageTops.lastIndex)
        state.panY = -(pageTops[i] * state.zoom)
        state.currentPage = i
        clampPan()
        requestRenderForVisiblePages()
    }

    // ─────────────────────────────────────────────────────────────
    // Rendering
    // ─────────────────────────────────────────────────────────────

    /**
     * Asks [RenderScheduler] to render every page currently in the viewport
     * (plus [ViewerConfig.prefetchDistance] pages on each side).
     *
     * This is a non-blocking call — the scheduler launches coroutines internally.
     * Safe to call frequently; the scheduler deduplicates in-flight requests.
     */
    fun requestRenderForVisiblePages() {
        if (!documentManager.isOpen || pageTops.isEmpty() || pageSizes.isEmpty()) {
            Log.w(
                TAG,
                "requestRenderForVisiblePages: skipping — " +
                        "isOpen=${documentManager.isOpen} " +
                        "pageTops=${pageTops.size} pageSizes=${pageSizes.size}"
            )
            return
        }

        val visible = visiblePageIndices()
        if (visible.isEmpty()) {
            Log.w(TAG, "requestRenderForVisiblePages: visiblePageIndices is EMPTY — " +
                    "panY=${state.panY} zoom=${state.zoom} vpH=$viewportHeight")
            return
        }

        Log.d(TAG, "requestRenderForVisiblePages: visible=$visible zoom=${state.zoom}")

        renderScheduler.requestRender(
            visiblePages = visible,
            config = PageRenderer.RenderConfig(
                zoomLevel        = state.zoom,
                renderQuality    = config.renderQuality,
                viewportWidthPx  = viewportWidth,
                nightMode        = config.isNightModeEnabled
            ),
            pageSizes = pageSizes
        )
    }

    // ─────────────────────────────────────────────────────────────
    // Pan clamping
    // ─────────────────────────────────────────────────────────────

    /**
     * Constrains [PdfViewerState.panX] and [PdfViewerState.panY] to the valid scroll range.
     *
     * ## Horizontal
     * - Zoom ≤ 1 (page fits within viewport): centre the page horizontally, panX = (vpW - scaledW) / 2.
     * - Zoom > 1 (page wider than viewport):  clamp panX to [-(scaledW - vpW), 0].
     *
     * ## Vertical
     * - Document shorter than viewport: centre vertically, panY = (vpH - scaledH) / 2.
     * - Document taller than viewport:  clamp panY to [vpH - scaledH, 0].
     */
    private fun clampPan() {
        if (viewportWidth == 0f || viewportHeight == 0f) return

        val scaledW = viewportWidth   * state.zoom
        val scaledH = totalDocHeight  * state.zoom

        state.panX = if (scaledW <= viewportWidth) {
            (viewportWidth - scaledW) / 2f
        } else {
            state.panX.coerceIn(-(scaledW - viewportWidth), 0f)
        }

        state.panY = if (scaledH <= viewportHeight) {
            (viewportHeight - scaledH) / 2f
        } else {
            state.panY.coerceIn(viewportHeight - scaledH, 0f)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────

    /**
     * Releases all resources: cancels the coroutine scope, closes the scheduler,
     * document manager, and evicts the bitmap cache.
     *
     * Called by [com.composepdf.PdfViewer] via [androidx.compose.runtime.DisposableEffect].
     */
    override fun close() {
        scope.cancel()
        renderScheduler.close()
        documentManager.close()
        bitmapCache.clear()
    }
}

