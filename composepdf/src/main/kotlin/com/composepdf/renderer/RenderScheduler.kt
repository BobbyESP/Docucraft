package com.composepdf.renderer

import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import com.composepdf.cache.BitmapCache
import com.composepdf.cache.BitmapPool
import com.composepdf.cache.PageCacheKey
import com.composepdf.cache.TileDiskCache
import com.composepdf.renderer.tiles.TileKey
import com.composepdf.state.PdfViewerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.roundToInt

private const val TAG = "PdfRenderScheduler"

/**
 * Orchestrates background rendering tasks for both full pages and high-resolution tiles.
 *
 * This class executes work; it does not decide *which* tiles should exist. That planning now
 * happens in [com.composepdf.renderer.tiles.TilePlanner], keeping responsibilities separate.
 */
internal class RenderScheduler internal constructor(
    private val documentManager: PdfDocumentManager,
    private val pageRenderer: PageRenderer,
    private val cache: BitmapCache,
    private val viewerState: PdfViewerState,
    private val bitmapPool: BitmapPool,
    private val tileDiskCache: TileDiskCache? = null,
    private val telemetry: RenderTelemetry? = null
) : Closeable {

    /** Dedicated dispatcher for CPU-intensive PDF rasterization.
     * Reduced to 2 threads to minimize native lock contention and concurrent memory pressure. */
    private val renderDispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
    private val scope = CoroutineScope(renderDispatcher + SupervisorJob())
    private val renderWindowTracker = RenderWindowTracker()

    /** Tracking of active page-level rendering jobs. */
    private val activeJobs = ConcurrentHashMap<Int, Job>()
    private val inFlightPageKeys = ConcurrentHashMap<Int, PageCacheKey>()

    /** Tracking of active high-res tile rendering jobs. */
    private val tileJobs = ConcurrentHashMap<String, Job>()

    /** Number of pages to pre-render outside the visible range in each direction. */
    var prefetchWindow: Int = 2
        set(value) { field = value.coerceAtLeast(0) }

    /**
     * Stable identifier for the currently open document.
     *
     * Used to prefix tile disk-cache keys so tiles from one PDF do not collide with
     * tiles from a different PDF that happens to share page indices. Set by
     * [com.composepdf.state.PdfViewerController] on each [open][PdfDocumentManager.open] call.
     */
    var docKey: String = ""

    private val _renderedPages = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())

    /** Observed by the UI to display base page bitmaps. */
    val renderedPages: StateFlow<Map<Int, Bitmap>> = _renderedPages.asStateFlow()

    fun onDocumentLoaded(documentKey: String) {
        docKey = documentKey
        renderWindowTracker.beginNewSession()
    }

    /**
     * Replaces the current desired high-resolution tile window.
     *
     * Tile publication is allowed only for keys that remain inside this keep-set. That lets a tile
     * started by an older render pass still publish when it is still relevant, while late results
     * for tiles that left the viewport are dropped deterministically.
     */
    fun updateTileWindow(keepKeys: Set<String>) {
        renderWindowTracker.updateDesiredTiles(keepKeys)
        tileJobs.keys.forEach { key -> //Consider creating an iterator with proper removal pattern (using toList may be heavy)
            if (key !in keepKeys) {
                tileJobs.remove(key)?.cancel()
                telemetry?.recordTileJobCancelled(key)
            }
        }
        telemetry?.recordActiveJobs(activeJobs.size, tileJobs.size)
    }

    /**
     * Schedules rendering for the given range of visible pages.
     *
     * @param visiblePages Range of indices currently visible in the viewport.
     * @param config Quality settings for the render.
     * @param pageSizes Original PDF dimensions for all pages.
     * @param getBaseWidth A function to retrieve the base width (at zoom 1.0) for a specific page index.
     */
    fun requestRender(
        visiblePages: IntRange,
        config: PageRenderer.RenderConfig,
        pageSizes: List<Size>,
        getBaseWidth: (Int) -> Float,
        renderPassId: Int
    ) {
        if (!documentManager.isOpen || pageSizes.isEmpty()) return

        val sessionToken = renderWindowTracker.currentSessionToken()
        val roundedZoom = (config.zoomLevel * 100f).roundToInt() / 100f
        val total = pageSizes.size

        val winStart = (visiblePages.first - prefetchWindow).coerceAtLeast(0)
        val winEnd = (visiblePages.last + prefetchWindow).coerceAtMost(total - 1)
        val window = winStart..winEnd
        retainRenderedPages(window.toSet())

        val desiredPages = linkedMapOf<Int, PageCacheKey>()
        window.forEach { pageIndex ->
            if (pageIndex !in 0 until total) return@forEach
            val pageSize = pageSizes[pageIndex]
            val baseWidth = getBaseWidth(pageIndex)
            val (targetW, targetH) = pageRenderer.calculateRenderSize(
                pageSize.width,
                pageSize.height,
                baseWidth,
                config
            )
            desiredPages[pageIndex] = PageCacheKey(pageIndex, roundedZoom, targetW, targetH)
        }
        renderWindowTracker.updateDesiredPages(desiredPages)

        // Evict jobs for pages that are no longer in the prefetch window
        activeJobs.keys.forEach { idx ->
            if (idx !in window) {
                activeJobs.remove(idx)?.cancel()
                inFlightPageKeys.remove(idx)
                telemetry?.recordActiveJobs(activeJobs.size, tileJobs.size)
            }
        }

        // Prioritize visible pages, then prefetched ones
        val ordered = visiblePages.toList() +
                (winStart until visiblePages.first).toList() +
                ((visiblePages.last + 1)..winEnd).toList()

        for (pageIndex in ordered) {
            if (pageIndex !in 0 until total) continue

            val pageSize = pageSizes[pageIndex]
            val baseWidth = getBaseWidth(pageIndex)
            val (targetW, targetH) = pageRenderer.calculateRenderSize(
                pageSize.width,
                pageSize.height,
                baseWidth,
                config
            )
            val cacheKey = PageCacheKey(pageIndex, roundedZoom, targetW, targetH)

            // Skip if already in memory or already being rendered for the same desired output.
            val cached = cache.get(cacheKey)
            if (cached != null) {
                telemetry?.recordPageMemoryHit(renderPassId, pageIndex, roundedZoom)
                if (renderWindowTracker.shouldPublishPage(sessionToken, pageIndex, cacheKey)) {
                    publishBitmap(pageIndex, cached)
                    telemetry?.recordPagePublished(renderPassId, pageIndex, stale = false)
                } else {
                    telemetry?.recordPagePublished(renderPassId, pageIndex, stale = true)
                }
                activeJobs.remove(pageIndex)?.cancel()
                inFlightPageKeys.remove(pageIndex)
                telemetry?.recordActiveJobs(activeJobs.size, tileJobs.size)
                continue
            }

            if (activeJobs[pageIndex]?.isActive == true && inFlightPageKeys[pageIndex] == cacheKey) continue

            activeJobs.remove(pageIndex)?.cancel()
            inFlightPageKeys[pageIndex] = cacheKey

            activeJobs[pageIndex] = scope.launch {
                telemetry?.recordActiveJobs(activeJobs.size, tileJobs.size)
                try {
                    val bitmap = documentManager.withPage(pageIndex) { page ->
                        pageRenderer.render(page, baseWidth, config)
                    }
                    telemetry?.recordPageRendered(renderPassId, pageIndex, roundedZoom)
                    cache.put(cacheKey, bitmap)
                    if (inFlightPageKeys[pageIndex] == cacheKey) {
                        inFlightPageKeys.remove(pageIndex)
                        activeJobs.remove(pageIndex)
                        if (renderWindowTracker.shouldPublishPage(sessionToken, pageIndex, cacheKey)) {
                            publishBitmap(pageIndex, bitmap)
                            telemetry?.recordPagePublished(renderPassId, pageIndex, stale = false)
                        } else {
                            telemetry?.recordPagePublished(renderPassId, pageIndex, stale = true)
                        }
                    }
                } catch (e: Exception) {
                    if (e !is CancellationException) Log.e(
                        TAG,
                        "Render error for page $pageIndex: ${e.message}"
                    )
                    inFlightPageKeys.remove(pageIndex)
                    activeJobs.remove(pageIndex)
                } finally {
                    telemetry?.recordActiveJobs(activeJobs.size, tileJobs.size)
                }
            }
        }
    }

    /**
     * Schedules a single tile render task.
     *
     * @param tileKey The key identifying the tile to render, including page index and coordinates.
     * @param baseWidth The width the page should have at zoom 1.0.
     */
    internal fun requestTile(tileKey: TileKey, baseWidth: Float, renderPassId: Int) {
        val sessionToken = renderWindowTracker.currentSessionToken()
        val memoryKey = tileKey.toCacheKey()
        val diskKey = tileKey.toDiskCacheKey(docKey)

        if (viewerState.getTile(memoryKey) != null) {
            telemetry?.recordTileMemoryHit(renderPassId, memoryKey)
            return
        }
        if (tileJobs.containsKey(memoryKey)) return

        tileJobs[memoryKey] = scope.launch {
            telemetry?.recordActiveJobs(activeJobs.size, tileJobs.size)
            try {
                val diskBitmap = tileDiskCache?.get(diskKey)
                if (diskBitmap != null) {
                    telemetry?.recordTileDiskHit(renderPassId, memoryKey)
                    if (renderWindowTracker.shouldPublishTile(sessionToken, memoryKey)) {
                        viewerState.putTile(memoryKey, diskBitmap)
                        telemetry?.recordTilePublished(renderPassId, memoryKey, stale = false)
                    } else {
                        bitmapPool.put(diskBitmap)
                        telemetry?.recordTilePublished(renderPassId, memoryKey, stale = true)
                    }
                    return@launch
                }

                val bitmap = documentManager.withPage(tileKey.pageIndex) { page ->
                    pageRenderer.renderTile(page, tileKey.rect, tileKey.zoom, baseWidth)
                }
                telemetry?.recordTileRendered(renderPassId, memoryKey)
                tileDiskCache?.put(diskKey, bitmap)
                if (renderWindowTracker.shouldPublishTile(sessionToken, memoryKey)) {
                    viewerState.putTile(memoryKey, bitmap)
                    telemetry?.recordTilePublished(renderPassId, memoryKey, stale = false)
                } else {
                    bitmapPool.put(bitmap)
                    telemetry?.recordTilePublished(renderPassId, memoryKey, stale = true)
                }
            } catch (e: Exception) {
                if (e !is CancellationException) Log.e(TAG, "Tile error for $memoryKey: ${e.message}")
            } finally {
                tileJobs.remove(memoryKey)
                telemetry?.recordActiveJobs(activeJobs.size, tileJobs.size)
            }
        }
    }

    /** Cancels all currently active tile rendering tasks. */
    fun cancelAllTiles() {
        updateTileWindow(emptySet())
    }

    private fun publishBitmap(pageIndex: Int, bitmap: Bitmap) {
        _renderedPages.update { current ->
            if (current[pageIndex] === bitmap) current
            else current + (pageIndex to bitmap)
        }
    }

    /**
     * Drops published base pages that left the active render window.
     *
     * Keeping them in [_renderedPages] makes the UI retain bitmaps indefinitely, which blocks the
     * delayed hand-off to [com.composepdf.cache.BitmapPool] and creates severe GC pressure while
     * scrolling through long documents.
     */
    private fun retainRenderedPages(pageIndices: Set<Int>) {
        _renderedPages.update { current ->
            val retained = current.filterKeys(pageIndices::contains)
            if (retained.size == current.size) current else retained
        }
    }

    /** Cancels everything and wipes the page cache. */
    fun invalidateAll() {
        renderWindowTracker.beginNewSession()
        activeJobs.keys.forEach { idx -> activeJobs.remove(idx)?.cancel() }
        inFlightPageKeys.clear()
        cancelAllTiles()
        _renderedPages.value = emptyMap()
        cache.clear()
    }

    override fun close() {
        invalidateAll()
        scope.cancel()
        renderDispatcher.close()
    }
}
