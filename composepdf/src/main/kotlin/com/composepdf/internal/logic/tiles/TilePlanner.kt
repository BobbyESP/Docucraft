package com.composepdf.internal.logic.tiles

import android.graphics.Rect
import com.composepdf.ScrollDirection
import com.composepdf.internal.logic.PageLayoutSnapshot
import com.composepdf.internal.service.pdf.PageRenderer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Responsible for determining which high-resolution image tiles need to be rendered or kept in memory
 * based on the current viewport state, zoom level, and page layout.
 *
 * This class implements a "stepped zoom" strategy to prevent visual artifacts and excessive re-rendering.
 * Instead of generating tiles for every infinitesimal zoom level, it snaps the zoom to discrete intervals
 * (powers of √2 by default). This ensures that tiles form a consistent grid, preventing sub-pixel gaps
 * and seams between adjacent tiles at extreme magnification.
 *
 * Key responsibilities include:
 * - Calculating visible tiles for the current screen bounds.
 * - Implementing a prefetch "halo" to load tiles just outside the viewport for smooth panning.
 * - Prioritizing tile requests based on their proximity to the center of the screen.
 * - Predicting upcoming pages based on scroll direction to warm up caches.
 *
 * @property tileSize The pixel dimensions (width and height) of a square tile. Defaults to [PageRenderer.TILE_SIZE].
 */
internal class TilePlanner(
    private val tileSize: Int = PageRenderer.TILE_SIZE
) {
    /**
     * Computes the set of tiles required to cover the current viewport for the given layout.
     *
     * @param viewport Current screen-space viewport bounds and offset.
     * @param layout Snapshot of the document layout containing page positions and sizes.
     * @param zoom The target magnification level.
     * @param visiblePages Range of indices for pages currently intersecting the viewport.
     * @param scrollDirectionHint A hint representing scroll movement (positive for forward,
     * negative for backward, 0 for static).
     * @param isTileCached A predicate to check if a tile is already present in the memory cache.
     * @return A [TilePlan] containing the target zoom level, keys to retain, and prioritized
     * tile requests.
     */
    fun computeTilePlan(
        viewport: ViewportState,
        layout: PageLayoutSnapshot,
        zoom: Float,
        visiblePages: IntRange,
        scrollDirectionHint: Int,
        isTileCached: (String) -> Boolean
    ): TilePlan {
        if (layout.isEmpty || visiblePages.isEmpty() || zoom <= 0f) {
            return TilePlan(
                steppedZoom = zoom,
                keepKeys = emptySet(),
                requests = emptyList(),
                prefetchPages = emptyList()
            )
        }

        val steppedZoom = computeSteppedZoom(zoom)
        val keepKeys = mutableSetOf<String>()
        val requests = mutableListOf<TileRequest>()

        val viewportCenterX = viewport.width / 2f
        val viewportCenterY = viewport.height / 2f

        val prefetchPages = buildList {
            val candidate = when {
                scrollDirectionHint > 0 -> visiblePages.last + 1
                scrollDirectionHint < 0 -> visiblePages.first - 1
                else -> -1
            }
            if (candidate in 0..layout.pageSizes.lastIndex && candidate !in visiblePages) {
                add(candidate)
            }
        }

        val scanPages = (visiblePages.toList() + prefetchPages).distinct()

        for (pageIndex in scanPages) {
            val isPrefetchPage = pageIndex !in visiblePages
            val pageWidth = layout.pageWidthPx(pageIndex)
            val pageHeight = layout.pageHeightPx(pageIndex)

            val pageTop: Float
            val pageLeft: Float

            if (layout.scrollDirection == ScrollDirection.VERTICAL) {
                pageTop = layout.pageTopDocY(pageIndex) * zoom + viewport.panY
                pageLeft = viewport.panX + (layout.corridorBreadth - pageWidth) * zoom / 2f
            } else {
                pageTop = viewport.panY + (layout.corridorBreadth - pageHeight) * zoom / 2f
                pageLeft = layout.pageLeftDocX(pageIndex) * zoom + viewport.panX
            }

            val pageBottom = pageTop + pageHeight * zoom
            val pageRight = pageLeft + pageWidth * zoom

            val visibleTop = maxOf(0f, pageTop)
            val visibleBottom = minOf(viewport.height, pageBottom)
            val visibleLeft = maxOf(0f, pageLeft)
            val visibleRight = minOf(viewport.width, pageRight)

            if (visibleBottom <= visibleTop || visibleRight <= visibleLeft) {
                if (!isPrefetchPage) continue
            }

            val scaleToStep = steppedZoom / zoom

            val localVisibleTop = (visibleTop - pageTop) * scaleToStep
            val localVisibleBottom = (visibleBottom - pageTop) * scaleToStep
            val localVisibleLeft = (visibleLeft - pageLeft) * scaleToStep
            val localVisibleRight = (visibleRight - pageLeft) * scaleToStep

            val pageWidthAtStep = pageWidth * steppedZoom
            val pageHeightAtStep = pageHeight * steppedZoom

            val maxTileCols = ceil(pageWidthAtStep / tileSize).toInt().coerceAtLeast(1)
            val maxTileRows = ceil(pageHeightAtStep / tileSize).toInt().coerceAtLeast(1)

            val epsilon = 0.001f
            val startX = floor((localVisibleLeft + epsilon) / tileSize).toInt().coerceAtLeast(0)
            val endX =
                ceil((localVisibleRight - epsilon) / tileSize).toInt().coerceAtMost(maxTileCols)
            val startY = floor((localVisibleTop + epsilon) / tileSize).toInt().coerceAtLeast(0)
            val endY =
                ceil((localVisibleBottom - epsilon) / tileSize).toInt().coerceAtMost(maxTileRows)

            val halo = if (isPrefetchPage) 0 else PREFETCH_HALO_TILES

            val tileStartX = (startX - halo).coerceAtLeast(0)
            val tileEndX = (endX + halo).coerceAtMost(maxTileCols)
            val tileStartY = (startY - halo).coerceAtLeast(0)
            val tileEndY = (endY + halo).coerceAtMost(maxTileRows)

            for (ty in tileStartY until tileEndY) {
                for (tx in tileStartX until tileEndX) {
                    val tileRect = Rect(
                        tx * tileSize,
                        ty * tileSize,
                        ((tx + 1) * tileSize).coerceAtMost(pageWidthAtStep.roundToInt()),
                        ((ty + 1) * tileSize).coerceAtMost(pageHeightAtStep.roundToInt())
                    )

                    val tileKey = TileKey.fromLayout(
                        pageIndex = pageIndex,
                        rect = tileRect,
                        zoom = steppedZoom,
                        baseWidth = pageWidth
                    )
                    val cacheKey = tileKey.toCacheKey()
                    keepKeys.add(cacheKey)

                    if (!isTileCached(cacheKey)) {
                        val tileCenterX =
                            ((tileRect.left + tileRect.right) / 2f / scaleToStep) + pageLeft
                        val tileCenterY =
                            ((tileRect.top + tileRect.bottom) / 2f / scaleToStep) + pageTop

                        val dx = tileCenterX - viewportCenterX
                        val dy = tileCenterY - viewportCenterY
                        val distanceSq =
                            if (isPrefetchPage) Float.MAX_VALUE else (dx * dx + dy * dy)

                        requests.add(TileRequest(tileKey, distanceSq))
                    }
                }
            }
        }

        return TilePlan(
            steppedZoom = steppedZoom,
            keepKeys = keepKeys,
            requests = requests.sortedBy { it.distanceSq },
            prefetchPages = prefetchPages
        )
    }

    internal fun computeSteppedZoom(zoom: Float): Float {
        val step = floor(ln(zoom.toDouble() / TILE_STEP_BASE) / ln(TILE_STEP_RATIO)).toInt()
            .coerceAtLeast(0)

        return (TILE_STEP_BASE * TILE_STEP_RATIO.pow(step.toDouble())).toFloat()
            .let { (it * 100).roundToInt() / 100f }
    }

    private companion object {
        const val TILE_STEP_BASE = 1.0f
        val TILE_STEP_RATIO = sqrt(2.0)
        const val PREFETCH_HALO_TILES = 1
    }
}

/**
 * Represents the calculated result of a tile planning operation.
 *
 * This data class encapsulates the set of tiles required to cover the current viewport at a specific
 * level of detail, along with metadata for cache management and asynchronous loading priorities.
 *
 * @property steppedZoom The normalized zoom level used for the tiles in this plan, calculated to
 * align with a fixed coordinate grid.
 * @property keepKeys A set of unique cache keys for all tiles that should remain in memory for
 * the current view (includes both visible and halo/prefetch tiles).
 * @property requests A prioritized list of [TileRequest] objects for tiles that are not currently
 * in the cache and need to be rendered.
 * @property prefetchPages A list of page indices that are not yet visible but are likely to be
 * viewed soon based on scroll direction.
 */
internal data class TilePlan(
    val steppedZoom: Float,
    val keepKeys: Set<String>,
    val requests: List<TileRequest>,
    val prefetchPages: List<Int>
)

/**
 * Represents a request to render a specific high-resolution tile.
 *
 * @property tileKey The unique identifier and metadata for the specific tile.
 * @property distanceSq The squared Euclidean distance from the tile's center to the viewport center.
 * Used to prioritize rendering tasks so that tiles closest to the user's focus are processed first.
 */
internal data class TileRequest(
    val tileKey: TileKey,
    val distanceSq: Float
)

/**
 * Represents the current state of the visible area (viewport) within the component.
 *
 * @property width The width of the viewport in screen pixels.
 * @property height The height of the viewport in screen pixels.
 * @property panX The horizontal offset (translation) of the content relative to the viewport.
 * @property panY The vertical offset (translation) of the content relative to the viewport.
 */
internal data class ViewportState(
    val width: Float,
    val height: Float,
    val panX: Float,
    val panY: Float
)
