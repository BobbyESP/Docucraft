package com.composepdf.internal.service.renderer

import com.composepdf.PdfViewerState
import com.composepdf.RenderTrigger
import com.composepdf.ViewerConfig
import com.composepdf.internal.logic.ViewerViewportCoordinator
import com.composepdf.internal.logic.tiles.TilePlanner
import com.composepdf.internal.service.pdf.PageRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

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

            val velocity = state.scrollVelocity
            val speed = abs(velocity.y) + abs(velocity.x)
            val isHighSpeed = speed > 2500f

            if (currentZoom > TILE_ZOOM_THRESHOLD && !isHighSpeed) {
                requestTilesForVisibleArea(
                    visiblePages = visiblePages,
                    currentZoom = currentZoom,
                    steppedZoom = steppedZoom,
                    renderPassId = renderPassId
                )
            } else {
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

private const val TILE_ZOOM_THRESHOLD = 1.1f

internal fun selectBasePageRenderZoom(currentZoom: Float, steppedZoom: Float): Float =
    if (currentZoom > TILE_ZOOM_THRESHOLD) steppedZoom else currentZoom
