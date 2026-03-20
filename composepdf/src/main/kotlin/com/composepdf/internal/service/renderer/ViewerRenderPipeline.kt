package com.composepdf.internal.service.renderer

import com.composepdf.internal.logic.tiles.TilePlanner
import com.composepdf.PdfViewerState
import com.composepdf.ViewerConfig
import com.composepdf.internal.logic.ViewerViewportCoordinator
import com.composepdf.internal.service.pdf.PageRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * High-level orchestration component that manages the rendering lifecycle of the PDF viewer.
 *
 * The [ViewerRenderPipeline] is responsible for:
 * - Coordinating between the viewport state, the tile planner, and the render scheduler.
 * - Deciding when to render low-resolution base pages versus high-resolution tiles based on the zoom level.
 * - Managing "render passes" to ensure UI consistency and performance during scroll and zoom operations.
 * - Implementing optimizations such as scroll direction hints and velocity-based tile skipping to maintain high frame rates.
 * - Reporting telemetry data for performance monitoring and debugging.
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

    fun recordPanDelta(panDeltaY: Float) {
        if (panDeltaY != 0f) {
            lastScrollDirectionHint = if (panDeltaY < 0f) 1 else -1
        }
    }

    fun onDocumentLoaded(documentKey: String) {
        renderScheduler.onDocumentLoaded(documentKey)
    }

    suspend fun invalidateTiles() {
        renderScheduler.updateTileWindow(emptySet())
        state.clearTiles()
    }

    /**
     * Triggers a new render pass for the currently visible pages in the viewport.
     *
     * This function calculates the necessary zoom levels, identifies visible pages, and coordinates
     * with the [RenderScheduler] to update both the base page textures and high-resolution tiles.
     * It accounts for current scroll velocity to skip tile rendering during high-speed flings
     * to maintain UI performance.
     *
     * @param trigger The reason for the render request (e.g., programmatic, user interaction, or layout change).
     */
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

        // Update active stepped zoom in state to prevent "Tile Soup" in the UI
        state.activeSteppedZoom = steppedZoom

        telemetry.recordPassStarted(
            passId = renderPassId,
            trigger = trigger,
            zoom = currentZoom,
            visiblePages = visiblePages
        )

        scope.launch {
            // Low zoom. Render only base pages and clear tiles
            if (currentZoom < TILE_ZOOM_THRESHOLD) {
                renderScheduler.updateTileWindow(emptySet())
                state.clearTiles()
            }

            // Render base pages (low-res)
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

            // Fling Consideration: If velocity is very high, skip tiles to keep scroll smooth
            val velocity = state.scrollVelocity
            val speed = abs(velocity.y) + abs(velocity.x)
            val isHighSpeed = speed > 2500f // Threshold for "High Speed" scroll

            if (currentZoom > TILE_ZOOM_THRESHOLD && !isHighSpeed) {
                // Render high-res tiles
                requestTilesForVisibleArea(
                    visiblePages = visiblePages,
                    currentZoom = currentZoom,
                    steppedZoom = steppedZoom,
                    renderPassId = renderPassId
                )
            } else {
                //Skip tiles to keep scroll smooth
                if (isHighSpeed) {
                    renderScheduler.updateTileWindow(emptySet())
                }
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

    /**
     * Calculates and schedules the rendering of high-resolution tiles for the currently visible
     * area of the document.
     *
     * This method determines which tiles are already in memory, which should be kept, and which
     * new tiles need to be requested from the [renderScheduler]. It also handles prefetching
     * tiles for pages adjacent to the visible range based on the scroll direction.
     *
     * @param visiblePages The range of page indices currently visible in the viewport.
     * @param currentZoom The exact current zoom level of the viewer.
     * @param steppedZoom The discretized zoom level used for tile generation to maintain cache consistency.
     * @param renderPassId A unique identifier for the current render pass, used for telemetry and task prioritization.
     */
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
            val isPrefetch = request.tileKey.pageIndex !in visiblePages
            renderScheduler.requestTile(
                tileKey = request.tileKey,
                baseWidth = viewportCoordinator.pageWidthPx(request.tileKey.pageIndex),
                distanceSq = request.distanceSq,
                isPrefetch = isPrefetch,
                renderPassId = renderPassId
            )
        }
    }
}

/** Minimum zoom level at which high-resolution tile rendering becomes worthwhile. */
private const val TILE_ZOOM_THRESHOLD = 1.1f

internal fun selectBasePageRenderZoom(currentZoom: Float, steppedZoom: Float): Float =
    if (currentZoom > TILE_ZOOM_THRESHOLD) steppedZoom else currentZoom
