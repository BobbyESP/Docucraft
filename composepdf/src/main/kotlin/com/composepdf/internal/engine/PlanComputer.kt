/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.engine

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure planning logic: given the current viewport, decide which base pages and tiles should exist
 * and which of them still need to be rendered.
 *
 * This runs on every viewport change (no debouncing). It must therefore stay allocation-light and
 * strictly O(visible tiles).
 */
internal class PlanComputer(private val tileSize: Int = TILE_SIZE) {

    /**
     * @param isTileAvailable Returns true when the tile is already rendered or currently being
     *   rendered — such tiles are desired but produce no new work.
     * @param isBaseAvailable Same contract for base page bitmaps at the given target width.
     */
    fun compute(
        inputs: PlanInputs,
        epoch: Int,
        isTileAvailable: (Long) -> Boolean,
        isBaseAvailable: (pageIndex: Int, targetWidth: Int) -> Boolean,
    ): RenderPlan {
        val layout = inputs.layout
        if (layout.isEmpty || inputs.zoom <= 0f) return RenderPlan.EMPTY

        val zoom = inputs.zoom
        val visiblePages = layout.visiblePageIndices(inputs.panX, inputs.panY, zoom)
        if (visiblePages.isEmpty()) return RenderPlan.EMPTY

        val pageCount = layout.pageSizes.size
        val work = ArrayList<RenderWork>(32)

        // ------------------------------------------------------------------ base pages
        val baseLevel = min(ZoomSteps.levelFor(zoom), 0)
        val baseScale = ZoomSteps.scaleFor(baseLevel)
        val baseWindow =
            (visiblePages.first - inputs.prefetchDistance).coerceAtLeast(0)..(visiblePages.last +
                        inputs.prefetchDistance)
                    .coerceAtMost(pageCount - 1)

        val desiredBaseWidths = HashMap<Int, Int>(baseWindow.count())
        val centerPage = (visiblePages.first + visiblePages.last) / 2
        for (page in baseWindow) {
            val pageWidth = layout.pageWidthPx(page)
            val pageHeight = layout.pageHeightPx(page)
            if (pageWidth <= 0f || pageHeight <= 0f) continue
            val (targetW, targetH) =
                basePageTargetSize(
                    baseWidth = pageWidth * baseScale,
                    aspectRatio = pageHeight / pageWidth,
                    quality = inputs.renderQuality,
                )
            desiredBaseWidths[page] = targetW
            if (!isBaseAvailable(page, targetW)) {
                val visible = page in visiblePages
                work +=
                    RenderWork.BasePage(
                        band = if (visible) Band.VISIBLE_BASE_PAGE else Band.PREFETCH_BASE_PAGE,
                        distance = abs(page - centerPage).toFloat(),
                        pageIndex = page,
                        targetWidth = targetW,
                        targetHeight = targetH,
                    )
            }
        }

        // ------------------------------------------------------------------ tiles
        val tileLevel = ZoomSteps.levelFor(zoom)
        val tilesWorthwhile =
            zoom > 1f && ZoomSteps.scaleFor(tileLevel) > inputs.renderQuality * 1.01f

        val speed = abs(inputs.velocityX) + abs(inputs.velocityY)
        val tilesFrozen = tilesWorthwhile && speed > HIGH_SPEED_PX_PER_S

        val desiredTilesByPage = HashMap<Int, Set<Long>>()
        val visibleRectByPage = HashMap<Int, TileRect>()

        if (tilesWorthwhile && !tilesFrozen) {
            val levelScale = ZoomSteps.scaleFor(tileLevel)
            planTiles(
                inputs = inputs,
                visiblePages = visiblePages,
                tileLevel = tileLevel,
                levelScale = levelScale,
                desiredTilesByPage = desiredTilesByPage,
                visibleRectByPage = visibleRectByPage,
                isTileAvailable = isTileAvailable,
                work = work,
            )
        }

        work.sortWith(compareBy({ it.band }, { it.distance }))

        return RenderPlan(
            epoch = epoch,
            tileLevel = if (tilesWorthwhile) tileLevel else null,
            tilesFrozen = tilesFrozen,
            visiblePages = visiblePages,
            desiredTilesByPage = desiredTilesByPage,
            visibleRectByPage = visibleRectByPage,
            baseWindow = baseWindow,
            desiredBaseWidths = desiredBaseWidths,
            work = work,
        )
    }

    private fun planTiles(
        inputs: PlanInputs,
        visiblePages: IntRange,
        tileLevel: Int,
        levelScale: Float,
        desiredTilesByPage: MutableMap<Int, Set<Long>>,
        visibleRectByPage: MutableMap<Int, TileRect>,
        isTileAvailable: (Long) -> Boolean,
        work: MutableList<RenderWork>,
    ) {
        val layout = inputs.layout
        val zoom = inputs.zoom
        val viewportCenterX = inputs.viewportWidth / 2f
        val viewportCenterY = inputs.viewportHeight / 2f

        // Predictive lookahead: extend the viewport opposite to the content motion so tiles that
        // are about to scroll in are rendered ahead of time. Velocity is finger/content velocity
        // in px/s: content moving down (vy > 0) reveals content above the viewport.
        val lookX =
            (abs(inputs.velocityX) * LOOKAHEAD_SECONDS).coerceAtMost(inputs.viewportWidth) +
                FIXED_HALO_PX
        val lookY =
            (abs(inputs.velocityY) * LOOKAHEAD_SECONDS).coerceAtMost(inputs.viewportHeight) +
                FIXED_HALO_PX

        val extLeft = if (inputs.velocityX > 0f) -lookX else -FIXED_HALO_PX
        val extRight = inputs.viewportWidth + if (inputs.velocityX < 0f) lookX else FIXED_HALO_PX
        val extTop = if (inputs.velocityY > 0f) -lookY else -FIXED_HALO_PX
        val extBottom = inputs.viewportHeight + if (inputs.velocityY < 0f) lookY else FIXED_HALO_PX

        // Pages potentially intersecting the extended viewport: visible range plus one page on
        // each side is always sufficient because the lookahead never exceeds a viewport dimension.
        val scanFirst = (visiblePages.first - 1).coerceAtLeast(0)
        val scanLast = (visiblePages.last + 1).coerceAtMost(layout.pageSizes.size - 1)

        for (page in scanFirst..scanLast) {
            val pageWidth = layout.pageWidthPx(page)
            val pageHeight = layout.pageHeightPx(page)

            // Screen-space page origin — the same math the UI uses to place pages.
            val pageTop = layout.pageScreenTop(page, inputs.panY, zoom)
            val pageLeft = layout.pageScreenLeft(page, inputs.panX, zoom)
            val pageRight = pageLeft + pageWidth * zoom
            val pageBottom = pageTop + pageHeight * zoom

            // Intersections with the strict and extended viewports, in screen space.
            val strictL = maxOf(0f, pageLeft)
            val strictT = maxOf(0f, pageTop)
            val strictR = minOf(inputs.viewportWidth, pageRight)
            val strictB = minOf(inputs.viewportHeight, pageBottom)

            val extL = maxOf(extLeft, pageLeft)
            val extT = maxOf(extTop, pageTop)
            val extR = minOf(extRight, pageRight)
            val extB = minOf(extBottom, pageBottom)
            if (extR <= extL || extB <= extT) continue

            // Convert to level space (page rasterized at levelScale).
            val toLevel = levelScale / zoom
            val pageWidthAtLevel = pageWidth * levelScale
            val pageHeightAtLevel = pageHeight * levelScale
            val maxCols = ceil(pageWidthAtLevel / tileSize).toInt().coerceAtLeast(1)
            val maxRows = ceil(pageHeightAtLevel / tileSize).toInt().coerceAtLeast(1)

            fun colOf(screenX: Float) =
                floor(((screenX - pageLeft) * toLevel + GRID_EPS) / tileSize).toInt()

            fun rowOf(screenY: Float) =
                floor(((screenY - pageTop) * toLevel + GRID_EPS) / tileSize).toInt()

            val startX = colOf(extL).coerceIn(0, maxCols - 1)
            val endX =
                (ceil(((extR - pageLeft) * toLevel - GRID_EPS) / tileSize).toInt()).coerceIn(
                    startX + 1,
                    maxCols,
                )
            val startY = rowOf(extT).coerceIn(0, maxRows - 1)
            val endY =
                (ceil(((extB - pageTop) * toLevel - GRID_EPS) / tileSize).toInt()).coerceIn(
                    startY + 1,
                    maxRows,
                )

            val hasStrict = strictR > strictL && strictB > strictT
            if (hasStrict) {
                // Visible rect in layout base space (zoom-independent), for stale retention.
                visibleRectByPage[page] =
                    TileRect(
                        left = floor((strictL - pageLeft) / zoom).toInt(),
                        top = floor((strictT - pageTop) / zoom).toInt(),
                        right = ceil((strictR - pageLeft) / zoom).toInt(),
                        bottom = ceil((strictB - pageTop) / zoom).toInt(),
                    )
            }

            val desired = HashSet<Long>((endX - startX) * (endY - startY))
            val toScreen = 1f / toLevel

            for (ty in startY until endY) {
                for (tx in startX until endX) {
                    val id = TileId.of(page, tileLevel, tx, ty)
                    desired.add(id.packed)
                    if (isTileAvailable(id.packed)) continue

                    val rect =
                        TileRect(
                            left = tx * tileSize,
                            top = ty * tileSize,
                            right = min((tx + 1) * tileSize, pageWidthAtLevel.roundToInt()),
                            bottom = min((ty + 1) * tileSize, pageHeightAtLevel.roundToInt()),
                        )
                    if (rect.width <= 0 || rect.height <= 0) continue

                    // Screen-space tile bounds for banding and priority.
                    val screenTileL = pageLeft + rect.left * toScreen
                    val screenTileT = pageTop + rect.top * toScreen
                    val screenTileR = pageLeft + rect.right * toScreen
                    val screenTileB = pageTop + rect.bottom * toScreen

                    val strictlyVisible =
                        hasStrict &&
                            screenTileR > strictL &&
                            screenTileL < strictR &&
                            screenTileB > strictT &&
                            screenTileT < strictB

                    val cx = (screenTileL + screenTileR) / 2f - viewportCenterX
                    val cy = (screenTileT + screenTileB) / 2f - viewportCenterY

                    work +=
                        RenderWork.Tile(
                            band = if (strictlyVisible) Band.VISIBLE_TILE else Band.LOOKAHEAD_TILE,
                            distance = cx * cx + cy * cy,
                            pageIndex = page,
                            id = id,
                            rect = rect,
                            levelScale = levelScale,
                            baseWidth = pageWidth,
                        )
                }
            }
            if (desired.isNotEmpty()) desiredTilesByPage[page] = desired
        }
    }

    companion object {
        const val TILE_SIZE = 512

        /** Above this finger/content speed tiles are not planned; base pages carry the frame. */
        const val HIGH_SPEED_PX_PER_S = 3500f

        /** How far ahead (in seconds of current velocity) tiles are speculatively rendered. */
        const val LOOKAHEAD_SECONDS = 0.25f

        /** Always plan slightly beyond the viewport so slow panning never hits a blank edge. */
        const val FIXED_HALO_PX = 96f

        private const val GRID_EPS = 0.01f

        const val MAX_BITMAP_PX = 2048

        /**
         * Target base-page bitmap size: layout width at the (zoom-capped-at-1) step, oversampled by
         * [quality], clamped to [MAX_BITMAP_PX] on both axes.
         */
        fun basePageTargetSize(
            baseWidth: Float,
            aspectRatio: Float,
            quality: Float,
        ): Pair<Int, Int> {
            val aspect = aspectRatio
            var w = baseWidth * quality
            var h = w * aspect
            if (w > MAX_BITMAP_PX) {
                w = MAX_BITMAP_PX.toFloat()
                h = w * aspect
            }
            if (h > MAX_BITMAP_PX) {
                h = MAX_BITMAP_PX.toFloat()
                w = h / aspect
            }
            return w.roundToInt().coerceAtLeast(1) to h.roundToInt().coerceAtLeast(1)
        }
    }
}
