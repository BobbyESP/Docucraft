package com.composepdf.state

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
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
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private const val TAG = "PdfViewerController"

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
        checkAndReleaseBitmap(bitmap)
    }

    private val documentManager = PdfDocumentManager(context)
    private val pageRenderer  = PageRenderer(bitmapPool)
    private val renderScheduler = RenderScheduler(documentManager, pageRenderer, bitmapCache)

    val renderedPages: StateFlow<Map<Int, Bitmap>> = renderScheduler.renderedPages

    var config by mutableStateOf(initialConfig)
        private set

    init {
        renderScheduler.prefetchWindow = config.prefetchDistance
    }

    private fun checkAndReleaseBitmap(bitmap: Bitmap) {
        scope.launch {
            delay(500) // Un poco más de margen para que Compose suelte la referencia
            withContext(Dispatchers.Main.immediate) {
                val isUsedInPages = renderedPages.value.values.any { it === bitmap }
                val isUsedInTiles = state.renderedTiles.values.any { it === bitmap }
                if (!isUsedInPages && !isUsedInTiles) {
                    bitmapPool.put(bitmap)
                }
            }
        }
    }

    fun updateConfig(newConfig: ViewerConfig) {
        if (config == newConfig) return
        config = newConfig
        renderScheduler.prefetchWindow = newConfig.prefetchDistance
        requestRenderForVisiblePages()
    }

    var viewportWidth by mutableFloatStateOf(0f)
        private set
    var viewportHeight by mutableFloatStateOf(0f)
        private set

    fun onViewportSizeChanged(width: Float, height: Float) {
        if (width == viewportWidth && height == viewportHeight) return
        viewportWidth  = width
        viewportHeight = height
        rebuildPageLayoutCache()
        clampPan()
        requestRenderForVisiblePages()
    }

    var pageSizes: List<Size> by mutableStateOf(emptyList())
        private set
    private var pageTops    = FloatArray(0)
    private var pageHeights = FloatArray(0)
    private var totalDocHeight = 0f
    private var layoutVersion by mutableIntStateOf(0)

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
            val h = viewportWidth * s.height.toFloat() / s.width.toFloat()
            pageTops[i] = y; pageHeights[i] = h
            y += h + spacing
        }
        totalDocHeight = (y - spacing).coerceAtLeast(0f)
        layoutVersion++
    }

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

    fun pageHeightPx(index: Int): Float = pageHeights.getOrNull(index) ?: viewportWidth
    fun pageTopDocY(index: Int): Float = pageTops.getOrNull(index) ?: 0f

    fun visiblePageIndices(): IntRange {
        val version = layoutVersion
        if (pageTops.isEmpty() || viewportHeight <= 0f) return IntRange.EMPTY
        val margin = config.pageSpacingPx * 0.5f
        val docTop = (-state.panY / state.zoom) - margin
        val docBottom = ((viewportHeight - state.panY) / state.zoom) + margin
        val first = findFirst(docTop); val last = findLast(docBottom)
        return if (first == -1 || last == -1 || first > last) IntRange.EMPTY else first..last
    }

    private fun findFirst(docTop: Float): Int {
        var low = 0; var high = pageTops.lastIndex; var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (pageTops[mid] + pageHeights[mid] >= docTop) { result = mid; high = mid - 1 } else low = mid + 1
        }
        return result
    }

    private fun findLast(docBottom: Float): Int {
        var low = 0; var high = pageTops.lastIndex; var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (pageTops[mid] <= docBottom) { result = mid; low = mid + 1 } else high = mid - 1
        }
        return result
    }

    fun currentPageFromPan(): Int {
        if (pageTops.isEmpty()) return 0
        return findLast((viewportHeight / 2f - state.panY) / state.zoom).coerceAtLeast(0)
    }

    fun isPointOverPage(point: Offset): Boolean {
        if (pageTops.isEmpty()) return false
        val scaledW = viewportWidth * state.zoom
        if (point.x !in state.panX..(state.panX + scaledW)) return false
        val index = findLast((point.y - state.panY) / state.zoom)
        if (index == -1) return false
        val docY = (point.y - state.panY) / state.zoom
        return docY in pageTops[index]..(pageTops[index] + pageHeights[index])
    }

    fun onGestureStart() { 
        state.isGestureActive = true 
        // No limpiamos tiles inmediatamente para permitir scroll fluido.
        // Solo los limpiaremos si detectamos un cambio de zoom significativo.
    }
    
    fun onGestureEnd() {
        state.isGestureActive = false
        clampPan()
        state.currentPage = currentPageFromPan()
        requestRenderForVisiblePages()
    }

    private var lastRenderedZoom = 1f

    fun onGestureUpdate(zoomChange: Float, panDelta: Offset, pivot: Offset) {
        if (viewportWidth == 0f) return
        val oldZoom = state.zoom
        val newZoom = (oldZoom * zoomChange).coerceIn(config.minZoom, config.maxZoom)
        
        if (newZoom != oldZoom) {
            val ratio = newZoom / oldZoom
            state.panX = pivot.x + (state.panX - pivot.x) * ratio
            state.panY = pivot.y + (state.panY - pivot.y) * ratio
            state.zoom = newZoom
            
            // Si el zoom cambia más de un 10%, limpiamos tiles para evitar distorsión visual
            if (Math.abs(newZoom - lastRenderedZoom) > lastRenderedZoom * 0.1f) {
                clearAllTiles()
                lastRenderedZoom = newZoom
            }
        }
        state.panX += panDelta.x; state.panY += panDelta.y
        clampPan()
        
        // Durante el scroll (panning), permitimos actualizar tiles visibles si el zoom es estable
        if (!state.isGestureActive || Math.abs(state.zoom - lastRenderedZoom) < 0.01f) {
            requestRenderForVisiblePages()
        }
    }

    fun onAnimatedZoomFrame(targetZoom: Float, pivot: Offset) {
        val oldZoom = state.zoom
        val newZoom = targetZoom.coerceIn(config.minZoom, config.maxZoom)
        if (newZoom == oldZoom) return
        val ratio = newZoom / oldZoom
        state.panX = pivot.x + (state.panX - pivot.x) * ratio
        state.panY = pivot.y + (state.panY - pivot.y) * ratio
        state.zoom = newZoom
        clampPan()
    }

    fun goToPage(index: Int) {
        if (pageTops.isEmpty()) return
        val i = index.coerceIn(0, pageTops.lastIndex)
        state.panY = -(pageTops[i] * state.zoom); state.currentPage = i
        clampPan(); requestRenderForVisiblePages()
    }

    fun requestRenderForVisiblePages() {
        if (!documentManager.isOpen || pageTops.isEmpty() || pageSizes.isEmpty()) return
        val visible = visiblePageIndices()
        if (visible.isEmpty()) return

        // Permitimos tiles de alta calidad si el zoom es alto, incluso si hay un gesto de PAN activo.
        val isHighResPossible = state.zoom > 1.2f 

        renderScheduler.requestRender(
            visiblePages = visible,
            config = PageRenderer.RenderConfig(
                zoomLevel = state.zoom,
                // Mejoramos la calidad base un poco (de 0.75 a 1.0) para que no sea tan borroso
                renderQuality = if (isHighResPossible) 1.0f else config.renderQuality,
                viewportWidthPx = viewportWidth,
                backgroundColor = android.graphics.Color.WHITE
            ),
            pageSizes = pageSizes
        )

        if (isHighResPossible) {
            requestTilesForVisibleArea()
        } else {
            clearAllTiles()
        }
    }

    private fun clearAllTiles() {
        val tiles = state.renderedTiles.values.toList()
        state.renderedTiles.clear()
        tiles.forEach { checkAndReleaseBitmap(it) }
        renderScheduler.cancelAllTiles()
    }

    private fun requestTilesForVisibleArea() {
        val visibleIndices = visiblePageIndices()
        if (visibleIndices.isEmpty()) return

        val zoom = (state.zoom * 100f).roundToInt() / 100f
        val zoomStr = zoom.toString()
        val tileSize = PageRenderer.TILE_SIZE

        val currentKeys = mutableSetOf<String>()

        for (pageIndex in visibleIndices) {
            val pageTopDoc = pageTops[pageIndex]
            val pageHeightDoc = pageHeights[pageIndex]
            
            val pageTopScreen = pageTopDoc * zoom + state.panY
            val pageBottomScreen = (pageTopDoc + pageHeightDoc) * zoom + state.panY
            
            val visibleTop = maxOf(0f, pageTopScreen).coerceIn(0f, viewportHeight)
            val visibleBottom = minOf(viewportHeight, pageBottomScreen).coerceIn(0f, viewportHeight)
            
            if (visibleBottom <= visibleTop) continue

            val startY = (visibleTop - pageTopScreen).coerceAtLeast(0f)
            val endY = (visibleBottom - pageTopScreen).coerceAtLeast(0f)
            val startX = (-state.panX).coerceAtLeast(0f)
            val endX = (viewportWidth - state.panX).coerceAtMost(viewportWidth * zoom)

            val firstTileX = floor(startX / tileSize).toInt()
            val lastTileX = ceil(endX / tileSize).toInt() - 1
            val firstTileY = floor(startY / tileSize).toInt()
            val lastTileY = ceil(endY / tileSize).toInt() - 1

            // Priorizamos los tiles centrales para que el usuario vea nitidez donde mira primero
            val centerY = (firstTileY + lastTileY) / 2
            val centerX = (firstTileX + lastTileX) / 2
            
            val tileCoordinates = mutableListOf<Pair<Int, Int>>()
            for (ty in firstTileY..lastTileY) {
                for (tx in firstTileX..lastTileX) {
                    tileCoordinates.add(tx to ty)
                }
            }
            
            // Ordenar por distancia al centro (Manhattan distance)
            tileCoordinates.sortBy { (tx, ty) -> 
                Math.abs(tx - centerX) + Math.abs(ty - centerY)
            }

            for ((tx, ty) in tileCoordinates) {
                val tileRect = Rect(tx * tileSize, ty * tileSize, (tx + 1) * tileSize, (ty + 1) * tileSize)
                val key = "${pageIndex}_${tileRect.left}_${tileRect.top}_${tileRect.right}_${tileRect.bottom}_$zoomStr"
                currentKeys.add(key)

                if (!state.renderedTiles.containsKey(key)) {
                    renderScheduler.requestTile(pageIndex, tileRect, zoom, viewportWidth) { bitmap ->
                        if ((state.zoom * 100f).roundToInt() / 100f == zoom) {
                            state.renderedTiles[key] = bitmap
                        } else {
                            checkAndReleaseBitmap(bitmap)
                        }
                    }
                }
            }
        }

        val entriesToRemove = state.renderedTiles.entries.filter { it.key !in currentKeys }
        entriesToRemove.forEach { (key, bitmap) ->
            state.renderedTiles.remove(key)
            checkAndReleaseBitmap(bitmap)
        }
    }

    private fun clampPan() {
        if (viewportWidth == 0f || viewportHeight == 0f) return
        val scaledW = viewportWidth * state.zoom
        val scaledH = totalDocHeight * state.zoom
        state.panX = if (scaledW <= viewportWidth) (viewportWidth - scaledW) / 2f
                     else state.panX.coerceIn(-(scaledW - viewportWidth), 0f)
        state.panY = if (scaledH <= viewportHeight) (viewportHeight - scaledH) / 2f
                     else state.panY.coerceIn(viewportHeight - scaledH, 0f)
    }

    override fun close() {
        scope.cancel()
        renderScheduler.close()
        documentManager.close()
        bitmapCache.clear()
    }
}
