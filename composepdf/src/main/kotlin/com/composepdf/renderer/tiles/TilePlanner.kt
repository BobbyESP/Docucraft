package com.composepdf.renderer.tiles

import android.graphics.Rect
import com.composepdf.layout.PageLayoutSnapshot
import com.composepdf.renderer.PageRenderer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Plans which high-resolution tiles should exist for the current viewport.
 *
 * This component is deliberately pure: it does not touch caches, dispatch rendering jobs,
 * or mutate viewer state. The controller asks for a plan, then applies it through
 * [com.composepdf.renderer.RenderScheduler].
 */
internal class TilePlanner(
    private val tileSize: Int = PageRenderer.TILE_SIZE
) {
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
        val keepKeys = linkedSetOf<String>()
        val requests = mutableListOf<TileRequest>()
        val viewportCenterX = viewport.width / 2f
        val viewportCenterY = viewport.height / 2f
        val prefetchPage = when {
            scrollDirectionHint > 0 -> (visiblePages.last + 1).coerceAtMost(layout.pageSizes.lastIndex)
            scrollDirectionHint < 0 -> (visiblePages.first - 1).coerceAtLeast(0)
            else -> -1
        }
        val prefetchPages = buildList {
            if (prefetchPage != -1 && prefetchPage !in visiblePages) add(prefetchPage)
        }
        val scanPages = buildList {
            addAll(visiblePages.toList())
            addAll(prefetchPages)
        }

        for (pageIndex in scanPages) {
            val isPrefetchPage = pageIndex !in visiblePages
            val pageWidth = layout.pageWidthPx(pageIndex)
            val pageHeight = layout.pageHeightPx(pageIndex)
            val pageTop = layout.pageTopDocY(pageIndex) * zoom + viewport.panY
            val pageBottom = pageTop + pageHeight * zoom
            val pageLeft = layout.pageScreenLeft(pageIndex, viewport.panX, zoom)

            val visibleTop =
                if (isPrefetchPage) pageTop else maxOf(0f, pageTop).coerceIn(0f, viewport.height)
            val visibleBottom =
                if (isPrefetchPage) pageBottom else minOf(viewport.height, pageBottom).coerceIn(
                    0f,
                    viewport.height
                )
            if (visibleBottom <= visibleTop) continue

            val steppedToCurrentScale = steppedZoom / zoom
            val startY = (visibleTop - pageTop) * steppedToCurrentScale
            val endY = (visibleBottom - pageTop) * steppedToCurrentScale
            val startX = (maxOf(pageLeft, 0f) - pageLeft) * steppedToCurrentScale
            val endX = (minOf(
                pageLeft + pageWidth * zoom,
                viewport.width
            ) - pageLeft) * steppedToCurrentScale
            val pageWidthAtStep = pageWidth * steppedZoom
            val pageHeightAtStep = pageHeight * steppedZoom
            val maxTileColumns = ceil(pageWidthAtStep / tileSize).toInt().coerceAtLeast(1)
            val maxTileRows = ceil(pageHeightAtStep / tileSize).toInt().coerceAtLeast(1)
            val haloTiles = if (isPrefetchPage) 0 else PREFETCH_HALO_TILES

            val tileStartY = (floor(startY / tileSize).toInt() - haloTiles).coerceAtLeast(0)
            val tileEndY = (ceil(endY / tileSize).toInt() + haloTiles).coerceAtMost(maxTileRows)
            val tileStartX = (floor(startX / tileSize).toInt() - haloTiles).coerceAtLeast(0)
            val tileEndX = (ceil(endX / tileSize).toInt() + haloTiles).coerceAtMost(maxTileColumns)

            for (tileY in tileStartY until tileEndY) {
                for (tileX in tileStartX until tileEndX) {
                    val tileRect = Rect(
                        tileX * tileSize,
                        tileY * tileSize,
                        (tileX + 1) * tileSize,
                        (tileY + 1) * tileSize
                    )
                    val tileKey = TileKey.fromLayout(
                        pageIndex = pageIndex,
                        rect = tileRect,
                        zoom = steppedZoom,
                        baseWidth = pageWidth
                    )
                    val cacheKey = tileKey.toCacheKey()
                    keepKeys += cacheKey

                    if (isTileCached(cacheKey)) continue

                    val distanceSq = if (isPrefetchPage) {
                        Float.MAX_VALUE
                    } else {
                        val tileCenterX = (tileRect.left + tileRect.right) / 2f
                        val tileCenterY = (tileRect.top + tileRect.bottom) / 2f
                        ((tileCenterX / steppedToCurrentScale + pageLeft) - viewportCenterX).pow(2) +
                                ((tileCenterY / steppedToCurrentScale + pageTop) - viewportCenterY).pow(
                                    2
                                )
                    }
                    requests += TileRequest(tileKey, distanceSq)
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
        const val TILE_STEP_BASE = 1.25f
        val TILE_STEP_RATIO = sqrt(2.0)
        const val PREFETCH_HALO_TILES = 1
    }
}

/** Full tile plan for a single viewport snapshot. */
internal data class TilePlan(
    val steppedZoom: Float,
    val keepKeys: Set<String>,
    val requests: List<TileRequest>,
    val prefetchPages: List<Int>
)

/** One prioritized tile render request. */
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
