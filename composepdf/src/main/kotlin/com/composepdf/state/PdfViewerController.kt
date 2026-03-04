package com.composepdf.state

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
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
import kotlin.math.*

/**
 * Central engine for PDF document management, geometry calculations, and render orchestration.
 *
 * This controller serves as the primary coordinator for the PDF viewer, managing:
 * 1. **Document Lifecycle**: Loading and closing documents via [PdfDocumentManager].
 * 2. **Layout Logic**: Calculating page positions, total document height, and viewport-to-document
 * coordinate mapping.
 * 3. **State Management**: Handling gesture updates for zooming and panning, including coordinate
 * clamping and viewport constraints.
 * 4. **Render Orchestration**: Managing parallel rendering of low-resolution base pages and
 * high-resolution tiles through the [RenderScheduler] based on the current viewport.
 *
 * @property context The application context used for document loading and bitmap management.
 * @property state The observable state object containing the current pan, zoom, and loaded tiles.
 * @property initialConfig Initial configuration for rendering quality, spacing, and zoom limits.
 * @property scope Coroutine scope for managing background rendering and cache cleanup.
 */
@Stable
class PdfViewerController(
    val context: Context,
    val state: PdfViewerState,
    initialConfig: ViewerConfig = ViewerConfig(),
    val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()),
    private val bitmapPool: BitmapPool = BitmapPool()
) : Closeable {

    constructor(context: Context, state: PdfViewerState, config: ViewerConfig) : this(
        context = context,
        state = state,
        initialConfig = config
    )

    private val bitmapCache = BitmapCache { bitmap ->
        // Safely return to pool after ensuring the UI is no longer drawing it
        scope.launch {
            delay(800)
            withContext(Dispatchers.Main.immediate) {
                val isUsedInPages = renderedPages.value.values.any { it === bitmap }
                val isUsedInTiles = state.getAllTiles().values.any { it === bitmap }
                if (!isUsedInPages && !isUsedInTiles) {
                    bitmapPool.put(bitmap)
                }
            }
        }
    }

    private val documentManager = PdfDocumentManager(context)
    private val pageRenderer  = PageRenderer(bitmapPool)
    private val renderScheduler = RenderScheduler(documentManager, pageRenderer, bitmapCache, state)

    /** Map of page indices to rendered low-resolution base bitmaps. */
    val renderedPages: StateFlow<Map<Int, Bitmap>> = renderScheduler.renderedPages

    var config by mutableStateOf(initialConfig)
        private set

    init {
        renderScheduler.prefetchWindow = config.prefetchDistance
    }

    /** Updates viewer configuration and refreshes renders if necessary. */
    fun updateConfig(newConfig: ViewerConfig) {
        if (config == newConfig) return
        config = newConfig
        renderScheduler.prefetchWindow = newConfig.prefetchDistance
        requestRenderForVisiblePages()
    }

    // --- Viewport Management ---

    var viewportWidth by mutableFloatStateOf(0f)
        private set
    var viewportHeight by mutableFloatStateOf(0f)
        private set

    /** Updates viewport size and recalculates page positions. */
    fun onViewportSizeChanged(width: Float, height: Float) {
        if (width == viewportWidth && height == viewportHeight) return
        viewportWidth  = width
        viewportHeight = height
        rebuildPageLayoutCache()
        clampPan()
        requestRenderForVisiblePages()
    }

    // --- Document Data ---

    /** Cached sizes of all pages in PDF points. */
    var pageSizes: List<Size> by mutableStateOf(emptyList())
        private set
    private var pageTops    = FloatArray(0)
    private var pageHeights = FloatArray(0)
    private var totalDocHeight = 0f
    private var layoutVersion by mutableIntStateOf(0)

    /** Pre-calculates the vertical position of every page at zoom 1.0. */
    private fun rebuildPageLayoutCache() {
        if (pageSizes.isEmpty() || viewportWidth == 0f) {
            pageTops = FloatArray(0); pageHeights = FloatArray(0); totalDocHeight = 0f
            layoutVersion++
            return
        }
        val count = pageSizes.size
        val spacing = config.pageSpacingPx
        pageTops = FloatArray(count); pageHeights = FloatArray(count)
        var y = 0f
        for (i in 0 until count) {
            val s = pageSizes[i]
            // Aspect-ratio based height scaling to match viewport width
            val h = viewportWidth * s.height.toFloat() / s.width.toFloat()
            pageTops[i] = y; pageHeights[i] = h
            y += h + spacing
        }
        totalDocHeight = (y - spacing).coerceAtLeast(0f)
        layoutVersion++
    }

    /** Loads a PDF from a [PdfSource] (Local, Asset, URI, or Remote). */
    fun loadDocument(source: PdfSource) {
        scope.launch {
            state.reset()
            try {
                if (source is PdfSource.Remote) loadRemote(source) else open(source)
            } catch (e: Exception) {
                state.error = e; state.isLoading = false
            }
        }
    }

    private suspend fun open(source: PdfSource) {
        documentManager.open(source)
        pageSizes = documentManager.getAllPageSizes()
        state.pageCount = documentManager.pageCount
        state.isLoading = false
        rebuildPageLayoutCache()
        clampPan()
        requestRenderForVisiblePages()
    }

    private suspend fun loadRemote(source: PdfSource.Remote) {
        RemotePdfLoader(context).load(source).collect { remote ->
            state.remoteState = remote
            if (remote is RemotePdfState.Cached) open(PdfSource.File(remote.file))
            else if (remote is RemotePdfState.Error) { state.error = Exception(remote.message); state.isLoading = false }
        }
    }

    // --- Geometry Helpers ---

    fun pageHeightPx(index: Int): Float = pageHeights.getOrNull(index) ?: viewportWidth
    fun pageTopDocY(index: Int): Float = pageTops.getOrNull(index) ?: 0f

    /** Binary search to find which pages are currently visible in the viewport. */
    fun visiblePageIndices(): IntRange {
        val version = layoutVersion // Observe layout version for invalidation
        if (pageTops.isEmpty() || viewportHeight <= 0f) return IntRange.EMPTY
        val margin = config.pageSpacingPx * 0.5f
        val docTop = (-state.panY / state.zoom) - margin
        val docBottom = ((viewportHeight - state.panY) / state.zoom) + margin
        
        var low = 0; var high = pageTops.lastIndex; var first = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (pageTops[mid] + pageHeights[mid] >= docTop) { first = mid; high = mid - 1 } else low = mid + 1
        }
        
        low = 0; high = pageTops.lastIndex; var last = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (pageTops[mid] <= docBottom) { last = mid; low = mid + 1 } else high = mid - 1
        }
        
        return if (first == -1 || last == -1 || first > last) IntRange.EMPTY else first..last
    }

    fun isPointOverPage(point: Offset): Boolean {
        if (pageTops.isEmpty()) return false
        val scaledW = viewportWidth * state.zoom
        if (point.x !in state.panX..(state.panX + scaledW)) return false
        // Search which page contains this Y
        val docY = (point.y - state.panY) / state.zoom
        var low = 0; var high = pageTops.lastIndex; var idx = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (pageTops[mid] <= docY) { idx = mid; low = mid + 1 } else high = mid - 1
        }
        if (idx == -1) return false
        return docY in pageTops[idx]..(pageTops[idx] + pageHeights[idx])
    }

    // --- Gesture Logic ---

    fun onGestureStart() { state.isGestureActive = true }
    fun onGestureEnd() {
        state.isGestureActive = false
        clampPan()
        // Update current page indicator based on vertical center
        state.currentPage = ( (viewportHeight / 2f - state.panY) / state.zoom ).let { centerDoc ->
            var low = 0; var high = pageTops.lastIndex; var idx = 0
            while (low <= high) {
                val mid = (low + high) ushr 1
                if (pageTops[mid] <= centerDoc) { idx = mid; low = mid + 1 } else high = mid - 1
            }
            idx
        }.coerceAtLeast(0)
        requestRenderForVisiblePages()
    }

    private var gestureJob: Job? = null

    /** Handles zoom and pan updates, with a small debounce to optimize rendering. */
    fun onGestureUpdate(zoomChange: Float, panDelta: Offset, pivot: Offset) {
        if (viewportWidth == 0f) return
        val oldZoom = state.zoom
        val newZoom = (oldZoom * zoomChange).coerceIn(config.minZoom, config.maxZoom)
        
        if (newZoom != oldZoom) {
            val ratio = newZoom / oldZoom
            state.panX = pivot.x + (state.panX - pivot.x) * ratio
            state.panY = pivot.y + (state.panY - pivot.y) * ratio
            state.zoom = newZoom
        }
        state.panX += panDelta.x; state.panY += panDelta.y
        clampPan()
        
        gestureJob?.cancel()
        gestureJob = scope.launch {
            delay(120)
            requestRenderForVisiblePages()
        }
    }

    /**
     * Updates zoom and pan absolute values during an animation frame.
     */
    fun onAnimatedZoomFrame(targetZoom: Float, pivot: Offset) {
        val oldZoom = state.zoom
        val newZoom = targetZoom.coerceIn(config.minZoom, config.maxZoom)
        if (newZoom == oldZoom) return
        
        val ratio = newZoom / oldZoom
        state.panX = pivot.x + (state.panX - pivot.x) * ratio
        state.panY = pivot.y + (state.panY - pivot.y) * ratio
        state.zoom = newZoom
        clampPan()
        
        // We trigger render update for each frame of animation to keep quality as high as possible
        requestRenderForVisiblePages()
    }

    // --- Rendering Orchestration ---

    /** Triggers rendering for base pages and high-res tiles for the current viewport. */
    fun requestRenderForVisiblePages() {
        if (!documentManager.isOpen || pageTops.isEmpty()) return
        val visible = visiblePageIndices()
        if (visible.isEmpty()) return

        val currentZoom = state.zoom

        // Cleanup: remove high-res tiles when zoomed out
        if (currentZoom < 1.1f) {
            state.clearTiles()
            renderScheduler.cancelAllTiles()
        } else {
            // Prune tiles with extreme scale difference (prevents "patch" artifacts)
            state.pruneTiles { key ->
                val tileZoom = key.split("_").lastOrNull()?.toFloatOrNull() ?: 1f
                val scale = currentZoom / tileZoom
                scale !in 0.4f..2.5f
            }
        }

        renderScheduler.requestRender(
            visiblePages = visible,
            config = PageRenderer.RenderConfig(
                zoomLevel = currentZoom,
                renderQuality = if (currentZoom > 1.1f) 1.0f else config.renderQuality,
                viewportWidthPx = viewportWidth
            ),
            pageSizes = pageSizes
        )

        if (currentZoom > 1.1f) requestTilesForVisibleArea()
    }

    /** 
     * Calculates and requests high-res tiles for the visible area. 
     * Uses sqrt(2) stepping for zoom to optimize cache hits.
     */
    private fun requestTilesForVisibleArea() {
        val visible = visiblePageIndices(); if (visible.isEmpty()) return
        val zoom = state.zoom
        
        // Use sqrt(2) based stepping (approx 1.41x) for stable tile rendering
        val base = 1.25f; val ratio = sqrt(2.0)
        val step = floor(ln(zoom.toDouble() / base) / ln(ratio)).toInt().coerceAtLeast(0)
        val steppedZoom = (base * ratio.pow(step.toDouble())).toFloat().let { (it * 100).roundToInt() / 100f }

        val tileSize = PageRenderer.TILE_SIZE
        val currentKeys = mutableSetOf<String>()
        val requests = mutableListOf<Triple<Int, Rect, Float>>() // pageIndex, rect, distanceToCenter

        val vpCenterX = viewportWidth / 2f; val vpCenterY = viewportHeight / 2f

        for (pageIndex in visible) {
            val pageTop = pageTops[pageIndex] * zoom + state.panY
            val vTop = maxOf(0f, pageTop).coerceIn(0f, viewportHeight)
            val vBottom = minOf(viewportHeight, pageTop + pageHeights[pageIndex] * zoom).coerceIn(0f, viewportHeight)
            if (vBottom <= vTop) continue

            val s = steppedZoom / zoom
            val sY = (vTop - pageTop) * s; val eY = (vBottom - pageTop) * s
            val sX = (maxOf(0f, -state.panX)) * s; val eX = (minOf(viewportWidth * zoom, viewportWidth - state.panX)) * s

            for (ty in floor(sY / tileSize).toInt()..<ceil(eY / tileSize).toInt()) {
                for (tx in floor(sX / tileSize).toInt()..<ceil(eX / tileSize).toInt()) {
                    val rect = Rect(tx * tileSize, ty * tileSize, (tx + 1) * tileSize, (ty + 1) * tileSize)
                    val key = "${pageIndex}_${rect.left}_${rect.top}_${rect.right}_${rect.bottom}_$steppedZoom"
                    currentKeys.add(key)
                    if (state.getTile(key) == null) {
                        val d = ( (rect.centerX() / s + state.panX) - vpCenterX ).pow(2) + 
                                ( (rect.centerY() / s + pageTop) - vpCenterY ).pow(2)
                        requests.add(Triple(pageIndex, rect, d.toFloat()))
                    }
                }
            }
        }
        
        renderScheduler.pruneTileJobs(currentKeys)
        requests.sortBy { it.third } // Global prioritization: center tiles first
        requests.forEach { renderScheduler.requestTile(it.first, it.second, steppedZoom, viewportWidth) }
    }

    private fun clampPan() {
        if (viewportWidth == 0f || viewportHeight == 0f) return
        val sW = viewportWidth * state.zoom; val sH = totalDocHeight * state.zoom
        state.panX = if (sW <= viewportWidth) (viewportWidth - sW) / 2f else state.panX.coerceIn(-(sW - viewportWidth), 0f)
        state.panY = if (sH <= viewportHeight) (viewportHeight - sH) / 2f else state.panY.coerceIn(viewportHeight - sH, 0f)
    }

    override fun close() {
        scope.cancel()
        renderScheduler.close()
        documentManager.close()
        bitmapCache.clear()
    }
}
