/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.engine

import android.util.Size
import com.composepdf.ScrollDirection
import com.composepdf.internal.engine.Band
import com.composepdf.internal.engine.PlanComputer
import com.composepdf.internal.engine.PlanInputs
import com.composepdf.internal.engine.RenderWork
import com.composepdf.internal.engine.TileId
import com.composepdf.internal.logic.PageLayoutSnapshot
import com.composepdf.internal.logic.ViewportMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanComputerTest {

    private val computer = PlanComputer()

    @Test
    fun atZoomOne_plansOnlyBasePages_visibleFirst() {
        val plan = computer.compute(inputs(zoom = 1f), epoch = 7, never(), neverBase())

        assertNull(plan.tileLevel)
        assertEquals(7, plan.epoch)
        assertEquals(0..0, plan.visiblePages)
        assertEquals(0..2, plan.baseWindow)
        assertTrue(plan.desiredTilesByPage.isEmpty())

        assertEquals(3, plan.work.size)
        assertTrue(plan.work.all { it is RenderWork.BasePage })
        assertEquals(Band.VISIBLE_BASE_PAGE, plan.work.first().band)
        assertEquals(0, plan.work.first().pageIndex)
        assertTrue(plan.work.drop(1).all { it.band == Band.PREFETCH_BASE_PAGE })
    }

    @Test
    fun basePageTargetWidth_followsZoomStepsBelowOne() {
        val planFull = computer.compute(inputs(zoom = 1f), 0, never(), neverBase())
        assertEquals(750, planFull.desiredBaseWidths.getValue(0)) // 500 * 1.0 * 1.5

        val planHalf = computer.compute(inputs(zoom = 0.5f), 0, never(), neverBase())
        assertEquals(375, planHalf.desiredBaseWidths.getValue(0)) // 500 * 0.5 * 1.5
    }

    @Test
    fun slightZoom_belowQualityGain_doesNotTile() {
        val plan = computer.compute(inputs(zoom = 1.2f), 0, never(), neverBase())
        assertNull(plan.tileLevel)
        assertTrue(plan.desiredTilesByPage.isEmpty())
    }

    @Test
    fun deepZoom_plansTilesCoveringTheViewport() {
        // zoom 3 -> level 4 (scale 4). Page 0 is 2000 level-px wide; the 500px viewport maps to
        // ~666 level px plus a 96px screen halo -> tile columns/rows 0..1.
        val plan = computer.compute(inputs(zoom = 3f), 0, never(), neverBase())

        assertEquals(4, plan.tileLevel)
        assertFalse(plan.tilesFrozen)
        val desired = plan.desiredTilesByPage.getValue(0)
        assertTrue(desired.contains(TileId.of(0, 4, 0, 0).packed))
        assertTrue(desired.contains(TileId.of(0, 4, 1, 0).packed))
        assertTrue(desired.contains(TileId.of(0, 4, 0, 1).packed))
        assertTrue(desired.contains(TileId.of(0, 4, 1, 1).packed))

        val tileWork = plan.work.filterIsInstance<RenderWork.Tile>()
        assertEquals(desired.size, tileWork.size)
        // Base page for the visible page is still the very first work item.
        assertEquals(Band.VISIBLE_BASE_PAGE, plan.work.first().band)
        // Visible tiles are sorted closest-to-center first.
        val visibleTiles = tileWork.filter { it.band == Band.VISIBLE_TILE }
        assertEquals(visibleTiles.sortedBy { it.distance }, visibleTiles)
    }

    @Test
    fun availableTiles_areDesiredButNotReRequested() {
        val cached = TileId.of(0, 4, 0, 0).packed
        val plan = computer.compute(inputs(zoom = 3f), 0, { it == cached }, neverBase())

        assertTrue(plan.desiredTilesByPage.getValue(0).contains(cached))
        val tileWork = plan.work.filterIsInstance<RenderWork.Tile>()
        assertTrue(tileWork.none { it.id.packed == cached })
    }

    @Test
    fun availableBasePages_produceNoWork() {
        val plan = computer.compute(inputs(zoom = 1f), 0, never(), { _, _ -> true })
        assertTrue(plan.work.isEmpty())
        assertEquals(3, plan.desiredBaseWidths.size)
    }

    @Test
    fun highSpeedFling_freezesTilesButKeepsBasePages() {
        val plan =
            computer.compute(
                inputs(zoom = 3f, velocityY = -8000f),
                0,
                never(),
                neverBase(),
            )

        assertTrue(plan.tilesFrozen)
        assertTrue(plan.desiredTilesByPage.isEmpty())
        assertTrue(plan.work.all { it is RenderWork.BasePage })
    }

    @Test
    fun velocity_extendsTilePlanningInMotionDirection() {
        val still = computer.compute(inputs(zoom = 3f), 0, never(), neverBase())
        val moving =
            computer.compute(
                inputs(zoom = 3f, velocityY = -2000f), // content moving up: reveal below
                0,
                never(),
                neverBase(),
            )

        val stillDesired = still.desiredTilesByPage.getValue(0)
        val movingDesired = moving.desiredTilesByPage.getValue(0)
        assertTrue(movingDesired.size > stillDesired.size)
        assertTrue(movingDesired.containsAll(stillDesired))

        val lookahead =
            moving.work.filterIsInstance<RenderWork.Tile>().filter {
                it.band == Band.LOOKAHEAD_TILE
            }
        assertTrue(lookahead.isNotEmpty())
        // Lookahead rows are below the strictly visible ones.
        assertTrue(lookahead.all { it.id.y >= 1 })
    }

    @Test
    fun emptyLayout_returnsEmptyPlan() {
        val plan =
            computer.compute(
                PlanInputs(
                    layout = PageLayoutSnapshot.empty(),
                    panX = 0f,
                    panY = 0f,
                    zoom = 1f,
                    viewportWidth = 500f,
                    viewportHeight = 500f,
                    velocityX = 0f,
                    velocityY = 0f,
                    renderQuality = 1.5f,
                    prefetchDistance = 2,
                ),
                0,
                never(),
                neverBase(),
            )
        assertTrue(plan.work.isEmpty())
        assertTrue(plan.visiblePages.isEmpty())
    }

    @Test
    fun basePageTargetSize_isCappedPerAxis() {
        val (w, h) =
            PlanComputer.basePageTargetSize(baseWidth = 3000f, aspectRatio = 1.4f, quality = 1.5f)
        assertTrue(w <= PlanComputer.MAX_BITMAP_PX)
        assertTrue(h <= PlanComputer.MAX_BITMAP_PX)
        assertEquals(1.4f, h.toFloat() / w.toFloat(), 0.01f)
    }

    // ------------------------------------------------------------------ helpers

    private fun never(): (Long) -> Boolean = { false }

    private fun neverBase(): (Int, Int) -> Boolean = { _, _ -> false }

    /** Three 500x500 pages stacked vertically with 20px spacing in a 500x500 viewport. */
    private fun layout(): PageLayoutSnapshot =
        PageLayoutSnapshot(
            pageSizes = List(3) { Size(1, 1) },
            pageOffsets = floatArrayOf(0f, 520f, 1040f),
            pageHeights = floatArrayOf(500f, 500f, 500f),
            pageWidths = floatArrayOf(500f, 500f, 500f),
            totalDocumentSize = 1540f,
            corridorBreadth = 500f,
            viewport = ViewportMetrics(500f, 500f),
            pageSpacingPx = 20f,
            scrollDirection = ScrollDirection.VERTICAL,
        )

    private fun inputs(
        zoom: Float,
        panX: Float = 0f,
        panY: Float = 0f,
        velocityX: Float = 0f,
        velocityY: Float = 0f,
    ): PlanInputs =
        PlanInputs(
            layout = layout(),
            panX = panX,
            panY = panY,
            zoom = zoom,
            viewportWidth = 500f,
            viewportHeight = 500f,
            velocityX = velocityX,
            velocityY = velocityY,
            renderQuality = 1.5f,
            prefetchDistance = 2,
        )
}
