package com.composepdf.renderer

import android.graphics.Bitmap
import android.util.Log
import com.composepdf.cache.BitmapCache
import com.composepdf.cache.PageCacheKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import kotlin.math.roundToInt

private const val TAG = "PdfRenderScheduler"

/**
 * Manages background rendering of PDF pages.
 *
 * ## Two-tier bitmap model
 *
 * [renderedPages] always holds the LATEST completed bitmap for each page index,
 * regardless of zoom. This means:
 *
 * - When zoom changes, [invalidateAll] cancels jobs + clears cache but keeps
 *   the old bitmaps in [renderedPages] as low-quality placeholders.
 * - New renders at the new zoom complete and OVERWRITE those entries.
 * - The UI sees a continuous stream of updates: old bitmap → new bitmap.
 *   No flash, no blank frames.
 *
 * ## In-flight deduplication
 *
 * [inFlightZoom] tracks what zoom each active job is rendering at.
 * If a new request arrives for the same page at the same zoom, the in-flight
 * job is left alone (it will arrive soon). If the zoom changed, the old job
 * is cancelled and a new one launched.
 */
class RenderScheduler(
    private val documentManager: PdfDocumentManager,
    private val pageRenderer: PageRenderer,
    private val cache: BitmapCache
) : Closeable {

    private val scope      = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val jobsMutex  = Mutex()
    private val activeJobs  = HashMap<Int, Job>()
    private val inFlightZoom = HashMap<Int, Float>()

    private val _renderedPages = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val renderedPages: StateFlow<Map<Int, Bitmap>> = _renderedPages.asStateFlow()

    var prefetchWindow: Int = 2
        set(value) { field = value.coerceAtLeast(0) }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Requests rendering for [visiblePages] ± [prefetchWindow] at the given [config].
     *
     * Decision per page:
     *  • Exact cache hit at current zoom → publish immediately, no job needed.
     *  • In-flight job at same zoom     → already coming, skip.
     *  • Anything else                  → cancel stale job, launch new one.
     */
    suspend fun requestRender(
        visiblePages: IntRange,
        config: PageRenderer.RenderConfig
    ) = jobsMutex.withLock {
        if (!documentManager.isOpen) {
            Log.w(TAG, "requestRender: document not open, skipping")
            return@withLock
        }

        val roundedZoom = roundZoom(config.zoomLevel)
        Log.d(TAG, "━━ requestRender pages=$visiblePages zoom=$roundedZoom quality=${config.renderQuality} vpW=${config.viewportWidthPx}")

        val total    = documentManager.pageCount
        val winStart = (visiblePages.first - prefetchWindow).coerceAtLeast(0)
        val winEnd   = (visiblePages.last  + prefetchWindow).coerceAtMost(total - 1)
        val window   = winStart..winEnd

        // Cancel jobs outside the current window.
        val iter = activeJobs.iterator()
        while (iter.hasNext()) {
            val (idx, job) = iter.next()
            if (idx !in window) {
                job.cancel(); inFlightZoom.remove(idx); iter.remove()
            }
        }

        // Visible pages first, then prefetch neighbours.
        val ordered = visiblePages.toList() +
                (winStart until visiblePages.first).toList() +
                ((visiblePages.last + 1)..winEnd).toList()

        for (pageIndex in ordered) {
            if (pageIndex !in 0 until total) continue

            val pageSize = documentManager.getPageSize(pageIndex)
            val (w, h)   = pageRenderer.calculateRenderSize(pageSize.width, pageSize.height, config)
            val cacheKey = PageCacheKey(pageIndex, roundedZoom, w, h)

            // 1. Exact cache hit → publish and skip.
            val cached = cache.get(cacheKey)
            if (cached != null) {
                Log.d(TAG, "  page $pageIndex CACHE HIT zoom=$roundedZoom ${cached.width}×${cached.height}px")
                publish(pageIndex, cached)
                activeJobs[pageIndex]?.cancel()
                inFlightZoom.remove(pageIndex)
                activeJobs.remove(pageIndex)
                continue
            }

            // 2. Already rendering at this exact zoom → let it finish.
            if (activeJobs[pageIndex]?.isActive == true && inFlightZoom[pageIndex] == roundedZoom) {
                Log.d(TAG, "  page $pageIndex IN-FLIGHT at zoom=$roundedZoom, skipping")
                continue
            }

            // 3. Cancel stale job (wrong zoom) and launch new one.
            activeJobs[pageIndex]?.let {
                Log.d(TAG, "  page $pageIndex cancelling stale job (was zoom=${inFlightZoom[pageIndex]}, now $roundedZoom)")
                it.cancel()
            }
            inFlightZoom[pageIndex] = roundedZoom

            Log.d(TAG, "  page $pageIndex LAUNCH zoom=$roundedZoom target=${w}×${h}px")

            activeJobs[pageIndex] = scope.launch {
                try {
                    val bitmap = documentManager.withPage(pageIndex) { page ->
                        pageRenderer.render(page, config)
                    }
                    val mbSize = bitmap.width * bitmap.height * 4 / 1_048_576
                    Log.d(TAG, "  page $pageIndex DONE zoom=$roundedZoom actual=${bitmap.width}×${bitmap.height}px (${mbSize}MB)")

                    cache.put(cacheKey, bitmap)
                    jobsMutex.withLock {
                        inFlightZoom.remove(pageIndex)
                        activeJobs.remove(pageIndex)
                    }
                    // Always publish — even if stale, the UI keeps the old bitmap
                    // until this new one arrives, then swaps atomically.
                    publish(pageIndex, bitmap)

                } catch (_: CancellationException) {
                    Log.d(TAG, "  page $pageIndex CANCELLED zoom=$roundedZoom")
                    jobsMutex.withLock { inFlightZoom.remove(pageIndex) }
                } catch (e: Exception) {
                    Log.e(TAG, "  page $pageIndex ERROR: ${e.message}", e)
                    jobsMutex.withLock { inFlightZoom.remove(pageIndex) }
                }
            }
        }
    }

    /**
     * Cancels all jobs and clears the bitmap cache.
     * Does NOT clear [_renderedPages] — stale bitmaps remain visible as placeholders.
     */
    suspend fun invalidateAll() = jobsMutex.withLock {
        Log.d(TAG, "invalidateAll: cancelling ${activeJobs.size} jobs, clearing cache")
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        inFlightZoom.clear()
        cache.clear()
        // _renderedPages deliberately NOT cleared.
    }

    suspend fun invalidatePage(pageIndex: Int) = jobsMutex.withLock {
        activeJobs[pageIndex]?.cancel()
        activeJobs.remove(pageIndex)
        inFlightZoom.remove(pageIndex)
        cache.clearPage(pageIndex)
        _renderedPages.update { it - pageIndex }
    }

    override fun close() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        inFlightZoom.clear()
    }

    private fun publish(pageIndex: Int, bitmap: Bitmap) {
        _renderedPages.update { it + (pageIndex to bitmap) }
    }

    private fun roundZoom(zoom: Float): Float = (zoom * 100f).roundToInt() / 100f
}