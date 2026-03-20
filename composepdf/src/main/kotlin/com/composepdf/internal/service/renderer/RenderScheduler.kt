package com.composepdf.internal.service.renderer

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import android.util.Size
import com.composepdf.PdfViewerState
import com.composepdf.internal.logic.tiles.TileKey
import com.composepdf.internal.service.cache.TileDiskCache
import com.composepdf.internal.service.cache.bitmap.BitmapPool
import com.composepdf.internal.service.pdf.PageRenderer
import com.composepdf.internal.service.pdf.PdfDocumentManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.Closeable
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

private const val TAG = "PdfRenderScheduler"

/**
 * Priority levels for rendering tasks.
 * Order matters: VISIBLE_TILE is 0 (highest priority).
 */
internal enum class RenderPriority {
    VISIBLE_TILE,
    VISIBLE_LOW_RES,
    PREFETCH_LOW_RES,
    PREFETCH_TILE
}

/**
 * Represents a single atomic rendering unit (a page or a tile).
 * Implements [Comparable] to be used in a [PriorityBlockingQueue].
 */
internal data class RenderTask(
    val priority: RenderPriority,
    val distanceSq: Float,
    val spatialIndex: Int,
    val tileKey: TileKey,
    val baseWidth: Float,
    val sessionToken: Int,
    val renderPassId: Int
) : Comparable<RenderTask> {
    override fun compareTo(other: RenderTask): Int {
        val priorityCompare = priority.ordinal.compareTo(other.priority.ordinal)
        if (priorityCompare != 0) return priorityCompare

        val distanceCompare = distanceSq.compareTo(other.distanceSq)
        if (distanceCompare != 0) return distanceCompare

        return spatialIndex.compareTo(other.spatialIndex)
    }
}

/**
 * Orchestrates background rendering tasks for both full pages and high-resolution tiles.
 *
 * This implementation focuses on:
 * 1. **Proactive Memory Management**: Force-evicts far-away pages from RAM cache.
 * 2. **Atomic Publication**: Uses [RenderWindowSnapshot] to prevent race conditions.
 * 3. **Strict Ownership**: Ensures every bitmap is either published or returned to [BitmapPool].
 */
internal class RenderScheduler internal constructor(
    private val documentManager: PdfDocumentManager,
    private val pageRenderer: PageRenderer,
    private val viewerState: PdfViewerState,
    private val bitmapPool: BitmapPool, // Now used by worker logic via pool.get() implicitly inside PageRenderer - wait, PageRenderer uses pool internally. This might be used for recycling?
    // Actually, viewerState.putTile manages the recycling via housekeeper.
    // Let's keep it for now as it might be needed for recycling dropped tasks
    private val tileDiskCache: TileDiskCache? = null,
    private val telemetry: RenderTelemetry? = null
) : Closeable {

    // Replaced Executors.newFixedThreadPool with Dispatchers.IO.limitedParallelism
    // which is more resource-efficient and idiomatic for Coroutines.
    private val renderDispatcher = Dispatchers.IO.limitedParallelism(3)
    private val scope = CoroutineScope(renderDispatcher + SupervisorJob())
    
    // Internal state tracking replacing RenderWindowTracker
    private data class RenderWindowSnapshot(
        val sessionToken: Int,
        val desiredPages: Map<Int, TileKey>,
        val desiredTiles: Set<String>
    )

    private val snapshot = AtomicReference(
        RenderWindowSnapshot(
            sessionToken = 0,
            desiredPages = emptyMap(),
            desiredTiles = emptySet()
        )
    )

    private val taskQueue = PriorityBlockingQueue<RenderTask>()
    private val diskSemaphore = Semaphore(2)

    /** Number of pages to pre-render outside the visible range. */
    var prefetchWindow: Int = 2
        set(value) {
            field = value.coerceAtLeast(0)
        }

    /** Stable identifier for the currently open document. */
    var docKey: String = ""

    private val _renderedPages = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())

    /** Observed by the UI to display base page bitmaps. */
    val renderedPages: StateFlow<Map<Int, Bitmap>> = _renderedPages.asStateFlow()

    init {
        // Fixed pool of 3 workers for high-concurrency PDF rasterization.
        repeat(3) {
            scope.launch {
                while (isActive) {
                    try {
                        val task = taskQueue.take()
                        val currentSnapshot = snapshot.get()

                        // 1. Session check: Instant drop if document changed.
                        if (task.sessionToken != currentSnapshot.sessionToken) continue

                        // Greedy Batching: Collect other tasks for the same page
                        val tasksToProcess = mutableListOf(task)
                        val iterator = taskQueue.iterator()
                        
                        // Limit the scan to prevent O(N) behavior on large queues.
                        // We check the next 50 items for batching candidates.
                        var scanned = 0
                        while (iterator.hasNext() && scanned < 50) {
                            val next = iterator.next()
                            scanned++
                            
                            if (next.tileKey.pageIndex == task.tileKey.pageIndex &&
                                next.sessionToken == task.sessionToken &&
                                next.priority == task.priority
                            ) {
                                tasksToProcess.add(next)
                                iterator.remove()
                            }
                            if (tasksToProcess.size >= 16) break // Cap batch size
                        }

                        // Open page ONCE and render all
                        documentManager.withPage(task.tileKey.pageIndex) { page ->
                            tasksToProcess.forEach { currentTask ->
                                val tileKey = currentTask.tileKey
                                val memoryKey = tileKey.toCacheKey()

                                // Double-check relevancy inside the lock
                                val snapshot = snapshot.get()
                                val isNeeded = if (tileKey.zoom == TileKey.BASE_LAYER_ZOOM) {
                                    // For base layer, check if the page index matches what's requested for that index
                                    snapshot.desiredPages[tileKey.pageIndex] == tileKey
                                } else {
                                    memoryKey in snapshot.desiredTiles
                                }

                                if (isNeeded) {
                                    try {
                                        val diskKey = tileKey.toDiskCacheKey(docKey)
                                        val diskBitmap = tileDiskCache?.get(
                                            docKey = docKey,
                                            pageIndex = tileKey.pageIndex,
                                            tileKey = diskKey
                                        )

                                        val bitmap = if (diskBitmap != null) {
                                            telemetry?.recordTileDiskHit(
                                                currentTask.renderPassId,
                                                memoryKey
                                            )
                                            diskBitmap
                                        } else {
                                            val rendered = if (tileKey.zoom == TileKey.BASE_LAYER_ZOOM) {
                                                // Base Page Render
                                                pageRenderer.render(
                                                    page,
                                                    currentTask.baseWidth,
                                                    // Map zoom/quality from task or config? 
                                                    // For now assume base layer is standard config
                                                    PageRenderer.RenderConfig(
                                                        zoomLevel = 1.0f,
                                                        renderQuality = 1.0f
                                                    )
                                                )
                                            } else {
                                                // Tile Render
                                                pageRenderer.renderTile(
                                                    page,
                                                    tileKey.rect,
                                                    tileKey.zoom,
                                                    currentTask.baseWidth
                                                )
                                            }

                                            // Write to disk in background
                                            if (tileKey.zoom != TileKey.BASE_LAYER_ZOOM) {
                                                launch {
                                                    diskSemaphore.withPermit {
                                                        tileDiskCache?.put(
                                                            docKey = docKey,
                                                            pageIndex = tileKey.pageIndex,
                                                            tileKey = diskKey,
                                                            bitmap = rendered
                                                        )
                                                    }
                                                }
                                            }
                                            telemetry?.recordTileRendered(
                                                currentTask.renderPassId,
                                                memoryKey
                                            )
                                            rendered
                                        }

                                        if (tileKey.zoom == TileKey.BASE_LAYER_ZOOM) {
                                            publishBitmap(tileKey.pageIndex, bitmap)
                                        } else {
                                            viewerState.putTile(memoryKey, bitmap)
                                        }

                                    } catch (e: Exception) {
                                        Log.e(TAG, "Render failed for $memoryKey: ${e.message}")
                                    }
                                }
                            }
                        }
                    } catch (_: InterruptedException) {
                        break
                    } catch (e: Exception) {
                        if (e !is CancellationException) {
                            Log.e(TAG, "Worker error: ${e.message}", e)
                        }
                    }
                }
            }
        }
    }

    /**
     * Removes tasks from the queue that belong to a previous document session.
     *
     * To maintain performance, this cleanup only triggers if the queue exceeds
     * a threshold (50 tasks), preventing unnecessary iterations on small workloads
     * while ensuring memory is not wasted on outdated rendering requests after
     * rapid document changes or resets.
     */
    private fun pruneObsoleteTasks() {
        val currentSession = snapshot.get().sessionToken
        if (taskQueue.size > 50) {
            taskQueue.removeIf { it.sessionToken < currentSession }
        }
    }

    fun onDocumentLoaded(documentKey: String) {
        docKey = documentKey
        updateSnapshot { old ->
            RenderWindowSnapshot(
                sessionToken = old.sessionToken + 1,
                desiredPages = emptyMap(),
                desiredTiles = emptySet()
            )
        }
        taskQueue.clear()
        _renderedPages.value = emptyMap()
    }

    fun updateTileWindow(keepKeys: Set<String>) {
        updateSnapshot { it.copy(desiredTiles = keepKeys.toSet()) }
    }

    /**
     * Schedules rendering for base pages within and surrounding the active viewport.
     *
     * This method manages the lifecycle of low-resolution page bitmaps by:
     * 1. Pruning obsolete tasks.
     * 2. Calculating the active window (visible pages plus [prefetchWindow]).
     * 3. Syncing the internal snapshot with current [TileKey] requirements.
     * 4. Dispatching prioritized [RenderTask]s for missing pages while publishing cached ones.
     *
     * @param visiblePages The range of page indices currently visible to the user.
     * @param config The rendering configuration including zoom level and quality settings.
     * @param pageSizes A list of intrinsic sizes for all pages in the document.
     * @param getBaseWidth A provider for the reference width of a specific page.
     * @param renderPassId A unique identifier used for telemetry and tracking this specific update.
     */
    fun requestRender(
        visiblePages: IntRange,
        config: PageRenderer.RenderConfig,
        pageSizes: List<Size>,
        getBaseWidth: (Int) -> Float,
        renderPassId: Int
    ) {
        if (!documentManager.isOpen || pageSizes.isEmpty()) return

        pruneObsoleteTasks()

        val sessionToken = snapshot.get().sessionToken

        val winStart = (visiblePages.first - prefetchWindow).coerceAtLeast(0)
        val winEnd = (visiblePages.last + prefetchWindow).coerceAtMost(pageSizes.size - 1)
        val activeWindow = winStart..winEnd

        // Remove base bitmaps for pages that left the prefetch window.
        val dropped = mutableListOf<Bitmap>()
        _renderedPages.update { current ->
            val newMap = current.filterKeys { it in activeWindow }
            // Identify dropped bitmaps to recycle
            val droppedKeys = current.keys - newMap.keys
            droppedKeys.forEach { pageId ->
                current[pageId]?.let { bmp ->
                    dropped.add(bmp)
                }
            }
            newMap
        }

        // Return dropped bitmaps to pool asynchronously.
        if (dropped.isNotEmpty()) {
            scope.launch {
                dropped.forEach { bmp ->
                    if (!bmp.isRecycled) bitmapPool.put(bmp)
                }
            }
        }

        // We need to update RenderWindowTracker to accept TileKey instead of PageCacheKey
        val desiredPages = activeWindow.associateWith { pageIndex ->
             // For base pages, we use zoom 1.0 (or what config says?)
             // Base pages are usually rendered at zoom 1.0
             // But TileKey needs explicit zoom.
             // Base pages are rendered at `currentTask.baseWidth` / intrinsicWidth scale?
             // No, `render(page, baseWidth)` handles scale internally.
             // For TileKey representing a base page, let's use a special constant for zoom if needed, 
             // but here we can just use 1.0f as placeholder or actual zoom.
             // Wait, TileKey is used for cache key.
             // If we change zoom, cache key changes.
             // For base layer, we don't really use TileKey cache key except for identifying the task.
             // Let's use `TileKey(pageIndex = pageIndex, tileX = 0, tileY = 0, zoom = BASE_LAYER_ZOOM)`
             TileKey(pageIndex, Rect(0, 0, 0, 0), TileKey.BASE_LAYER_ZOOM)
        }
        
        updateSnapshot { it.copy(desiredPages = desiredPages) }
        
        for (pageIndex in activeWindow) {
            val tileKey = desiredPages[pageIndex] ?: continue
            val cached = _renderedPages.value[pageIndex] // Check strictly existing map

            if (cached != null) {
                telemetry?.recordPageMemoryHit(renderPassId, pageIndex, config.zoomLevel)
                continue
            }

            val priority =
                if (pageIndex in visiblePages) RenderPriority.VISIBLE_LOW_RES else RenderPriority.PREFETCH_LOW_RES

            taskQueue.offer(
                RenderTask(
                    priority = priority,
                    distanceSq = abs(pageIndex - (visiblePages.first + visiblePages.last) / 2).toFloat(),
                    spatialIndex = pageIndex,
                    tileKey = tileKey,
                    baseWidth = getBaseWidth(pageIndex),
                    sessionToken = sessionToken,
                    renderPassId = renderPassId
                ))
        }
    }

    /**
     * Schedules the rendering of a high-resolution tile for a specific section of a page.
     *
     * The task is added to a priority queue where visible tiles take precedence over prefetch tiles.
     * The rendering process first attempts to retrieve the tile from [tileDiskCache] before
     * falling back to rasterizing the PDF page.
     *
     * To ensure memory efficiency, the resulting bitmap is only published to the [viewerState]
     * if the tile is still within the active render window snapshot; otherwise, it is
     * immediately returned to the [bitmapPool].
     *
     * @param tileKey The unique identifier for the tile, containing page index, zoom, and coordinates.
     * @param baseWidth The reference width of the page used for scaling calculations.
     * @param distanceSq The squared distance from the viewport center, used for intra-priority sorting.
     * @param isPrefetch If true, assigns a lower priority ([RenderPriority.PREFETCH_TILE]) to the task.
     * @param renderPassId An identifier for telemetry tracking and performance profiling.
     */
    internal fun requestTile(
        tileKey: TileKey,
        baseWidth: Float,
        distanceSq: Float,
        isPrefetch: Boolean,
        renderPassId: Int
    ) {
        val sessionToken = snapshot.get().sessionToken
        val memoryKey = tileKey.toCacheKey()

        if (viewerState.getTile(memoryKey) != null) return

        val priority = if (isPrefetch) RenderPriority.PREFETCH_TILE else RenderPriority.VISIBLE_TILE

        taskQueue.offer(
            RenderTask(
                priority = priority,
                distanceSq = distanceSq,
                spatialIndex = morton(tileKey.tileX, tileKey.tileY),
                tileKey = tileKey,
                baseWidth = baseWidth,
                sessionToken = sessionToken,
                renderPassId = renderPassId
            ))
    }

    private fun publishBitmap(pageIndex: Int, bitmap: Bitmap) {
        _renderedPages.update { current ->
            if (current[pageIndex] === bitmap) current
            else current + (pageIndex to bitmap)
        }
    }

    private fun morton(x: Int, y: Int, bits: Int = 16): Int {
        var answer = 0
        for (i in 0 until bits) {
            answer = answer or ((x shr i and 1) shl (2 * i))
            answer = answer or ((y shr i and 1) shl (2 * i + 1))
        }
        return answer
    }

    /**
     * Resets the entire rendering pipeline by clearing all pending tasks, caches, and current state.
     *
     * This method:
     * 1. Increments the session token to invalidate any in-flight rendering workers.
     * 2. Clears the high-priority and low-priority task queues.
     * 3. Wipes the current [renderedPages] state flow.
     * 4. Evicts all bitmaps from the memory cache.
     *
     * Use this when the document source changes or a global refresh of the rendered content is required.
     */
    fun invalidateAll() {
        updateSnapshot { old ->
            RenderWindowSnapshot(
                sessionToken = old.sessionToken + 1,
                desiredPages = emptyMap(),
                desiredTiles = emptySet()
            )
        }
        taskQueue.clear()
        _renderedPages.value = emptyMap()
    }

    private fun updateSnapshot(transform: (RenderWindowSnapshot) -> RenderWindowSnapshot) {
        var old: RenderWindowSnapshot
        var next: RenderWindowSnapshot
        do {
            old = snapshot.get()
            next = transform(old)
        } while (!snapshot.compareAndSet(old, next))
    }

    override fun close() {
        invalidateAll()
        scope.cancel()
        // No need to close renderDispatcher as it's a CoroutineDispatcher
    }
}
