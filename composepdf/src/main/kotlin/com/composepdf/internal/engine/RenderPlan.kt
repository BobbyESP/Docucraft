/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.engine

import com.composepdf.internal.logic.PageLayoutSnapshot

/** Axis-aligned tile rectangle in level-space pixels (page rasterized at the level's scale). */
internal data class TileRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int
        get() = right - left

    val height: Int
        get() = bottom - top
}

/** Priority bands. Lower band renders first; ties are broken by [RenderWork.distance]. */
internal object Band {
    const val VISIBLE_BASE_PAGE = 0
    const val VISIBLE_TILE = 1
    const val LOOKAHEAD_TILE = 2
    const val PREFETCH_BASE_PAGE = 3
}

/** A single unit of rasterization work, produced by [PlanComputer]. */
internal sealed interface RenderWork {
    val band: Int
    val distance: Float
    val pageIndex: Int

    data class Tile(
        override val band: Int,
        override val distance: Float,
        override val pageIndex: Int,
        val id: TileId,
        val rect: TileRect,
        val levelScale: Float,
        val baseWidth: Float,
    ) : RenderWork

    data class BasePage(
        override val band: Int,
        override val distance: Float,
        override val pageIndex: Int,
        val targetWidth: Int,
        val targetHeight: Int,
    ) : RenderWork
}

/**
 * Immutable description of everything the engine should be doing right now.
 *
 * Produced by [PlanComputer] from the latest viewport, consumed by the work queue (what to render
 * next), the [TileStore] (which tiles to keep published) and the [PageBitmapStore] (which base
 * pages to keep alive).
 */
internal class RenderPlan(
    val epoch: Int,
    /** Current tile level, or null when tiles are not worthwhile at this zoom. */
    val tileLevel: Int?,
    /**
     * True when tile planning was skipped because the viewport is moving too fast. Published tiles
     * are left untouched in that case.
     */
    val tilesFrozen: Boolean,
    val visiblePages: IntRange,
    /** All current-level tiles that should exist (visible + lookahead), per page. */
    val desiredTilesByPage: Map<Int, Set<Long>>,
    /**
     * Visible rectangle of each relevant page in layout base space (zoom-independent px). Used by
     * [TileStore] to decide which stale-level tiles are still worth showing.
     */
    val visibleRectByPage: Map<Int, TileRect>,
    /** Pages whose base bitmap should be kept in memory. */
    val baseWindow: IntRange,
    /** Desired base bitmap width per page inside [baseWindow]. */
    val desiredBaseWidths: Map<Int, Int>,
    /** Missing work, sorted by ascending (band, distance). */
    val work: List<RenderWork>,
) {
    companion object {
        val EMPTY =
            RenderPlan(
                epoch = -1,
                tileLevel = null,
                tilesFrozen = false,
                visiblePages = IntRange.EMPTY,
                desiredTilesByPage = emptyMap(),
                visibleRectByPage = emptyMap(),
                baseWindow = IntRange.EMPTY,
                desiredBaseWidths = emptyMap(),
                work = emptyList(),
            )
    }
}

/** Snapshot of every input the planner needs, captured on the UI side. */
internal class PlanInputs(
    val layout: PageLayoutSnapshot,
    val panX: Float,
    val panY: Float,
    val zoom: Float,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val velocityX: Float,
    val velocityY: Float,
    val renderQuality: Float,
    val prefetchDistance: Int,
)
