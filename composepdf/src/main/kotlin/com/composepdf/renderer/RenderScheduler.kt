package com.composepdf.renderer

import android.graphics.Bitmap
import android.util.Log
import android.util.Size
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
import kotlinx.coroutines.withContext
import java.io.Closeable
import kotlin.math.roundToInt

private const val TAG = "PdfRenderScheduler"

/**
 * Orchestrates background rendering of PDF pages.
 *
 * ## Design principles
 *
 * - **No shared mutex between jobs**: active-job tracking uses only main-thread access
 *   via [CoroutineScope] on [Dispatchers.Main.immediate]. This eliminates the deadlock that
 *   occurred when the old [jobsMutex] was acquired both from the scheduler entry-point AND
 *   from within individual render jobs (nested lock on the same dispatcher → coroutine suspend).
 *
 * - **Page sizes are passed in** from the controller (which already has them cached in
 *   [com.composepdf.state.PdfViewerController.pageSizes]), so this class never calls back
 *   into [PdfDocumentManager] for metadata — only for actual pixel rendering via [withPage].
 *
 * - **Stale-bitmap placeholder strategy**: [_renderedPages] is never cleared on zoom change.
 *   Old bitmaps stay visible while new ones render at the updated zoom, then are atomically
 *   swapped. This eliminates blank-page flashes during pinch-zoom.
 *
 * - **Thread safety without mutex**: [activeJobs] and [inFlightZoom] are mutated exclusively
 *   on [Dispatchers.Main.immediate] (both at the call-site and in post-render cleanup via
 *   [withContext]). Only the actual [PdfRenderer.Page.render] call runs on [Dispatchers.IO].
 */
class RenderScheduler(
    private val documentManager: PdfDocumentManager,
    private val pageRenderer: PageRenderer,
    private val cache: BitmapCache
) : Closeable {

    /**
     * Scope for job lifecycle management. Bound to [Dispatchers.Main.immediate] so that
     * mutations to [activeJobs] and [inFlightZoom] are always on the main thread —
     * no mutex required. Render work is dispatched to [Dispatchers.IO] inside each job.
     */
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    /**
     * One active render [Job] per page index.
     * **Accessed only from the main thread.**
     */
    private val activeJobs = HashMap<Int, Job>()

    /**
     * The zoom level at which each page is currently being rendered.
     * **Accessed only from the main thread** (same as [activeJobs]).
     */
    private val inFlightZoom = HashMap<Int, Float>()

    /** Number of pages to pre-render beyond the visible range on each side. */
    var prefetchWindow: Int = 2
        set(value) { field = value.coerceAtLeast(0) }

    /**
     * Published bitmaps keyed by page index. Compose collects this [StateFlow] and
     * triggers recomposition only when a specific page bitmap changes.
     */
    private val _renderedPages = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val renderedPages: StateFlow<Map<Int, Bitmap>> = _renderedPages.asStateFlow()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Schedules renders for [visiblePages] ± [prefetchWindow].
     *
     * **Must be called from the Main thread** — [PdfViewerController] always satisfies
     * this because its scope runs on [Dispatchers.Main.immediate].
     *
     * Decision tree per page:
     *  1. Exact cache hit at current zoom → publish immediately, cancel any stale job.
     *  2. In-flight job at same zoom      → already in progress, skip.
     *  3. Otherwise                       → cancel stale job, launch new render on IO.
     *
     * @param visiblePages Inclusive range of page indices currently visible on screen.
     * @param config       Render parameters: zoom, quality, viewport width, night mode…
     * @param pageSizes    Pre-fetched page sizes from the controller's local cache.
     *                     Passing these in avoids re-acquiring the document mutex.
     */
    fun requestRender(
        visiblePages: IntRange,
        config: PageRenderer.RenderConfig,
        pageSizes: List<Size>
    ) {
        if (!documentManager.isOpen || pageSizes.isEmpty()) {
            Log.w(TAG, "requestRender: document not open or no page sizes, skipping")
            return
        }

        val roundedZoom = roundZoom(config.zoomLevel)
        val total = pageSizes.size

        Log.d(
            TAG,
            "━━ requestRender pages=$visiblePages zoom=$roundedZoom " +
                    "quality=${config.renderQuality} vpW=${config.viewportWidthPx}"
        )

        val winStart = (visiblePages.first - prefetchWindow).coerceAtLeast(0)
        val winEnd   = (visiblePages.last  + prefetchWindow).coerceAtMost(total - 1)
        val window   = winStart..winEnd

        // Cancel and remove jobs that fell outside the prefetch window.
        val iter = activeJobs.iterator()
        while (iter.hasNext()) {
            val (idx, job) = iter.next()
            if (idx !in window) {
                Log.d(TAG, "  page $idx evicted from window, cancelling")
                job.cancel()
                inFlightZoom.remove(idx)
                iter.remove()
            }
        }

        // Visible-first ordering so the user sees results before prefetch neighbours.
        val ordered = visiblePages.toList() +
                (winStart until visiblePages.first).toList() +
                ((visiblePages.last + 1)..winEnd).toList()

        for (pageIndex in ordered) {
            if (pageIndex !in 0 until total) continue

            val pageSize = pageSizes[pageIndex]
            val (targetW, targetH) = pageRenderer.calculateRenderSize(
                pageSize.width, pageSize.height, config
            )
            val cacheKey = PageCacheKey(pageIndex, roundedZoom, targetW, targetH)

            // 1. Exact cache hit → publish immediately, no render needed.
            val cached = cache.get(cacheKey)
            if (cached != null) {
                Log.d(
                    TAG,
                    "  page $pageIndex CACHE HIT zoom=$roundedZoom " +
                            "${cached.width}×${cached.height}px"
                )
                publishBitmap(pageIndex, cached)
                activeJobs.remove(pageIndex)?.cancel()
                inFlightZoom.remove(pageIndex)
                continue
            }

            // 2. Already rendering at this exact zoom → let the in-flight job finish.
            if (activeJobs[pageIndex]?.isActive == true && inFlightZoom[pageIndex] == roundedZoom) {
                Log.d(TAG, "  page $pageIndex IN-FLIGHT zoom=$roundedZoom, skipping")
                continue
            }

            // 3. Cancel stale job (different zoom) and launch a new render.
            activeJobs[pageIndex]?.let { stale ->
                Log.d(
                    TAG,
                    "  page $pageIndex cancelling stale job " +
                            "(was zoom=${inFlightZoom[pageIndex]}, now $roundedZoom)"
                )
                stale.cancel()
            }

            inFlightZoom[pageIndex] = roundedZoom
            Log.d(TAG, "  page $pageIndex LAUNCH zoom=$roundedZoom target=${targetW}×${targetH}px")

            // Capture immutable references for the coroutine closure.
            val capturedIndex   = pageIndex
            val capturedKey     = cacheKey
            val capturedConfig  = config

            activeJobs[pageIndex] = scope.launch {
                // Heavy work (PdfRenderer.Page.render) runs on IO.
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        documentManager.withPage(capturedIndex) { page ->
                            pageRenderer.render(page, capturedConfig)
                        }
                    }

                    val mb = bitmap.width * bitmap.height * 4 / 1_048_576
                    Log.d(
                        TAG,
                        "  page $capturedIndex DONE zoom=$roundedZoom " +
                                "actual=${bitmap.width}×${bitmap.height}px (${mb}MB)"
                    )

                    cache.put(capturedKey, bitmap)

                    // Back on Main.immediate — safe to mutate job maps without a lock.
                    inFlightZoom.remove(capturedIndex)
                    activeJobs.remove(capturedIndex)

                    publishBitmap(capturedIndex, bitmap)

                } catch (_: CancellationException) {
                    Log.d(TAG, "  page $capturedIndex CANCELLED zoom=$roundedZoom")
                    // Mutations on Main.immediate (parent scope).
                    inFlightZoom.remove(capturedIndex)
                    activeJobs.remove(capturedIndex)
                } catch (e: Exception) {
                    Log.e(TAG, "  page $capturedIndex ERROR: ${e.message}", e)
                    inFlightZoom.remove(capturedIndex)
                    activeJobs.remove(capturedIndex)
                }
            }
        }
    }

    /**
     * Cancels all in-flight render jobs and evicts the bitmap cache.
     *
     * Deliberately does **not** clear [_renderedPages] — old bitmaps act as placeholders
     * while new renders arrive at the updated zoom level, preventing blank-page flashes.
     *
     * **Must be called from the Main thread.**
     */
    fun invalidateAll() {
        Log.d(TAG, "invalidateAll: cancelling ${activeJobs.size} jobs, clearing cache")
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        inFlightZoom.clear()
        cache.clear()
        // _renderedPages intentionally NOT cleared.
    }

    /**
     * Invalidates a single page: cancels its render job, removes cache entries for that
     * page across all zoom levels, and removes its entry from [_renderedPages].
     *
     * Useful for selective re-renders (e.g. toggling night mode on one page).
     *
     * **Must be called from the Main thread.**
     */
    fun invalidatePage(pageIndex: Int) {
        activeJobs.remove(pageIndex)?.cancel()
        inFlightZoom.remove(pageIndex)
        cache.clearPage(pageIndex)
        _renderedPages.update { it - pageIndex }
    }

    /** Cancels all jobs and releases scope resources. Called from [PdfViewerController.close]. */
    override fun close() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        inFlightZoom.clear()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Atomically inserts or replaces [bitmap] for [pageIndex] in [_renderedPages].
     *
     * [StateFlow.update] uses CAS semantics internally — safe to call from any thread,
     * though in practice we call it from Main after render completion.
     *
     * Skips the update (no-op) when the bitmap reference hasn't changed to avoid
     * triggering unnecessary recompositions.
     */
    private fun publishBitmap(pageIndex: Int, bitmap: Bitmap) {
        _renderedPages.update { current ->
            if (current[pageIndex] === bitmap) current
            else current + (pageIndex to bitmap)
        }
    }

    /**
     * Rounds [zoom] to 2 decimal places to prevent floating-point drift from causing
     * spurious cache misses between consecutive frames at the same logical zoom step.
     *
     * Example: 2.4999998 and 2.5000001 both round to 2.50 → same [PageCacheKey].
     */
    private fun roundZoom(zoom: Float): Float = (zoom * 100f).roundToInt() / 100f
}