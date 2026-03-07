package com.composepdf.renderer

import com.composepdf.renderer.tiles.TilePlanner
import com.composepdf.state.PdfViewerState
import com.composepdf.state.ViewerConfig
import com.composepdf.state.ViewerViewportCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * High-level render pipeline for the viewer.
 *
 * Responsibilities:
 * - decide when base-page rendering is needed
 * - prune outdated tiles as zoom changes
 * - ask [TilePlanner] for visible/prefetch tile work
 * - dispatch actual work to [RenderScheduler]
 * - emit internal telemetry so render bugs can be diagnosed from pass metadata and cache behavior
 */
internal class ViewerRenderPipeline(
    private val scope: CoroutineScope,
    private val state: PdfViewerState,
    private val viewportCoordinator: ViewerViewportCoordinator,
    private val renderScheduler: RenderScheduler,
    private val tilePlanner: TilePlanner,
    private val telemetry: RenderTelemetry,
    private val configProvider: () -> ViewerConfig,
    private val isDocumentOpen: () -> Boolean
) {
    private var lastScrollDirectionHint: Int = 0
    private var nextRenderPassId: Int = 0
    private var activeTileZoomBucket: Float? = null

    fun recordPanDelta(panDeltaY: Float) {
        if (panDeltaY != 0f) {
            lastScrollDirectionHint = if (panDeltaY < 0f) 1 else -1
        }
    }

    /**
     * Starts a fresh document render session.
     *
     * This resets the scheduler's publication token so late results from a previous document can no
     * longer reach the UI, even if background rasterization finishes after cancellation.
     */
    fun onDocumentLoaded(documentKey: String) {
        activeTileZoomBucket = null
        renderScheduler.onDocumentLoaded(documentKey)
    }

    /**
     * Clears all currently published/high-res tile work.
     *
     * Used when the page geometry changes (viewport resize, fit mode change, spacing change) so the
     * viewer never composites tiles that were rendered against an older base layout.
     */
    suspend fun invalidateTiles() {
        activeTileZoomBucket = null
        renderScheduler.updateTileWindow(emptySet())
        state.clearTiles()
    }

    fun requestRenderForVisiblePages(trigger: RenderTrigger = RenderTrigger.PROGRAMMATIC) {
        if (!isDocumentOpen() || !viewportCoordinator.hasLayout) return

        viewportCoordinator.updateCurrentPageFromViewport()
        val visiblePages = viewportCoordinator.visiblePageIndices()
        if (visiblePages.isEmpty()) return

        val currentZoom = state.zoom
        val steppedZoom = tilePlanner.computeSteppedZoom(currentZoom)
        val basePageRenderZoom = selectBasePageRenderZoom(currentZoom, steppedZoom)
        val pageSizesSnapshot = viewportCoordinator.pageSizes
        val config = configProvider()
        val renderPassId = ++nextRenderPassId

        telemetry.recordPassStarted(
            passId = renderPassId,
            trigger = trigger,
            zoom = currentZoom,
            visiblePages = visiblePages
        )

        scope.launch {
            if (currentZoom < TILE_ZOOM_THRESHOLD) {
                activeTileZoomBucket = null
                renderScheduler.updateTileWindow(emptySet())
                state.clearTiles()
            } else if (activeTileZoomBucket != steppedZoom) {
                // We no longer prune tiles immediately here.
                // The UI (PdfPage) already filters tiles by baseWidthKey and zoom level.
                // Keeping old tiles visible during transitions prevents "flashing" or
                // tiles disappearing before new ones are ready.
                // The LruTileCache will naturally evict them when memory is needed.
                activeTileZoomBucket = steppedZoom
            }

            renderScheduler.requestRender(
                visiblePages = visiblePages,
                config = PageRenderer.RenderConfig(
                    zoomLevel = basePageRenderZoom,
                    renderQuality = if (currentZoom > TILE_ZOOM_THRESHOLD) 1.0f else config.renderQuality
                ),
                pageSizes = pageSizesSnapshot,
                getBaseWidth = viewportCoordinator::pageWidthPx,
                renderPassId = renderPassId
            )

            if (currentZoom > TILE_ZOOM_THRESHOLD) {
                requestTilesForVisibleArea(visiblePages, currentZoom, steppedZoom, renderPassId)
            } else {
                telemetry.recordTilePlan(
                    passId = renderPassId,
                    steppedZoom = steppedZoom,
                    keepTileCount = 0,
                    requestCount = 0,
                    prefetchPages = emptyList()
                )
            }
        }
    }

    private fun requestTilesForVisibleArea(
        visiblePages: IntRange,
        currentZoom: Float,
        steppedZoom: Float,
        renderPassId: Int
    ) {
        val tilePlan = tilePlanner.computeTilePlan(
            viewport = viewportCoordinator.viewportState(),
            layout = viewportCoordinator.snapshot(),
            zoom = currentZoom,
            visiblePages = visiblePages,
            scrollDirectionHint = lastScrollDirectionHint,
            isTileCached = { cacheKey ->
                val cached = state.getTile(cacheKey) != null
                if (cached) {
                    telemetry.recordTileMemoryHit(renderPassId, cacheKey)
                }
                cached
            }
        )

        telemetry.recordTilePlan(
            passId = renderPassId,
            steppedZoom = steppedZoom,
            keepTileCount = tilePlan.keepKeys.size,
            requestCount = tilePlan.requests.size,
            prefetchPages = tilePlan.prefetchPages
        )

        renderScheduler.updateTileWindow(tilePlan.keepKeys)
        tilePlan.requests.forEach { request ->
            renderScheduler.requestTile(
                tileKey = request.tileKey,
                baseWidth = viewportCoordinator.pageWidthPx(request.tileKey.pageIndex),
                renderPassId = renderPassId
            )
        }
    }
}

/** Minimum zoom level at which high-resolution tile rendering becomes worthwhile. */
private const val TILE_ZOOM_THRESHOLD = 1.1f

internal fun selectBasePageRenderZoom(currentZoom: Float, steppedZoom: Float): Float =
    if (currentZoom > TILE_ZOOM_THRESHOLD) steppedZoom else currentZoom
