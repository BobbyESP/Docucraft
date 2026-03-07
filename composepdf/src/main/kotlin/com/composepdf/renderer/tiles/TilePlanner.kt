package com.composepdf.renderer.tiles

import android.graphics.Rect
import com.composepdf.layout.PageLayoutSnapshot
import com.composepdf.renderer.PageRenderer
import com.composepdf.state.ScrollDirection
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Plans which high-resolution tiles should exist for the current viewport.
 * 
 * Refined to use normalized coordinate grids to prevent sub-pixel seams (gaps) between tiles
 * at extreme zoom levels.
 */
internal class TilePlanner(
    private val tileSize: Int = PageRenderer.TILE_SIZE
) {
    /**
     * Computes the set of tiles required to cover the current viewport for the given layout.
     * 
     * @param viewport Current screen-space viewport bounds and offset.
     * @param layout Snapshot of the document layout (page positions/sizes).
     * @param zoom The target magnification level.
     * @param visiblePages Range of pages currently intersecting the viewport.
     * @param scrollDirectionHint 1 for scrolling forward, -1 for backward, 0 for static.
     * @param isTileCached Check if a tile is already in the memory cache.
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

        // 1. Identify prefetch pages based on scroll direction to warm up low-res textures.
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
            
            // Calculate page position in screen coordinates based on scroll direction
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

            // Clip visible area of the page to the viewport
            val visibleTop = maxOf(0f, pageTop)
            val visibleBottom = minOf(viewport.height, pageBottom)
            val visibleLeft = maxOf(0f, pageLeft)
            val visibleRight = minOf(viewport.width, pageRight)

            if (visibleBottom <= visibleTop || visibleRight <= visibleLeft) {
                if (!isPrefetchPage) continue // Page not visible
            }

            // Map screen visible bounds to "stepped-zoom" page coordinates
            // This is the critical math section to avoid seams.
            val scaleToStep = steppedZoom / zoom
            
            // Bounds in stepped-zoom pixels
            val localVisibleTop = (visibleTop - pageTop) * scaleToStep
            val localVisibleBottom = (visibleBottom - pageTop) * scaleToStep
            val localVisibleLeft = (visibleLeft - pageLeft) * scaleToStep
            val localVisibleRight = (visibleRight - pageLeft) * scaleToStep

            val pageWidthAtStep = pageWidth * steppedZoom
            val pageHeightAtStep = pageHeight * steppedZoom
            
            val maxTileCols = ceil(pageWidthAtStep / tileSize).toInt().coerceAtLeast(1)
            val maxTileRows = ceil(pageHeightAtStep / tileSize).toInt().coerceAtLeast(1)

            // Calculate tile indices. Using a small epsilon to avoid edge-case "ghost" tiles.
            val epsilon = 0.001f
            val startX = floor((localVisibleLeft + epsilon) / tileSize).toInt().coerceAtLeast(0)
            val endX = ceil((localVisibleRight - epsilon) / tileSize).toInt().coerceAtMost(maxTileCols)
            val startY = floor((localVisibleTop + epsilon) / tileSize).toInt().coerceAtLeast(0)
            val endY = ceil((localVisibleBottom - epsilon) / tileSize).toInt().coerceAtMost(maxTileRows)

            // Halo: Prefetch tiles immediately adjacent to the viewport to enable smooth panning.
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
                        // Priority is based on distance to viewport center in screen space
                        val tileCenterX = ((tileRect.left + tileRect.right) / 2f / scaleToStep) + pageLeft
                        val tileCenterY = ((tileRect.top + tileRect.bottom) / 2f / scaleToStep) + pageTop
                        
                        val dx = tileCenterX - viewportCenterX
                        val dy = tileCenterY - viewportCenterY
                        val distanceSq = if (isPrefetchPage) Float.MAX_VALUE else (dx * dx + dy * dy)
                        
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

    /**
     * Logic to snap current zoom to a fixed scale step.
     * This prevents "Tile Soup" by ensuring all visible high-res tiles belong to the same power-of-sqrt(2) grid.
     */
    internal fun computeSteppedZoom(zoom: Float): Float {
        val step = floor(ln(zoom.toDouble() / TILE_STEP_BASE) / ln(TILE_STEP_RATIO)).toInt()
            .coerceAtLeast(0)

        return (TILE_STEP_BASE * TILE_STEP_RATIO.pow(step.toDouble())).toFloat()
            .let { (it * 100).roundToInt() / 100f }
    }

    private companion object {
        const val TILE_STEP_BASE = 1.0f // Baseline zoom 1.0
        val TILE_STEP_RATIO = sqrt(2.0) // sqrt(2) steps provide ~2x area increase per step
        const val PREFETCH_HALO_TILES = 1 // Render 1 extra tile around the viewport
    }
}

internal data class TilePlan(
    val steppedZoom: Float,
    val keepKeys: Set<String>,
    val requests: List<TileRequest>,
    val prefetchPages: List<Int>
)

internal data class TileRequest(
    val tileKey: TileKey,
    val distanceSq: Float
)

/** Runtime viewport state used during tile planning. */
internal data class ViewportState(
    val width: Float,
    val height: Float,
    val panX: Float,
    val panY: Float
)
