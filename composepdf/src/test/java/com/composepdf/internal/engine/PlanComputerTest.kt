/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.engine

import com.composepdf.internal.logic.PageLayoutSnapshot
import com.composepdf.internal.logic.ViewportMetrics
import com.composepdf.internal.logic.layoutOf
import com.composepdf.internal.logic.threePages
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
                inputs(zoom = 1f, layout = PageLayoutSnapshot.empty()),
                0,
                never(),
                neverBase(),
            )
        assertTrue(plan.work.isEmpty())
        assertTrue(plan.visiblePages.isEmpty())
    }

    @Test
    fun tallPage_whoseBaseIsCapped_tilesAtZoomOne() {
        // A 500 × 4000 page (a OneNote export): its base would be 750 × 6000, capped to 256 × 2048,
        // half of what the screen shows. Tiles at level 0 give it its 500 px back.
        val tall =
            layoutOf(floatArrayOf(500f), floatArrayOf(4000f), 20f, ViewportMetrics(500f, 500f))
        val plan = computer.compute(inputs(zoom = 1f, layout = tall), 0, never(), neverBase())

        assertEquals(256, plan.desiredBaseWidths.getValue(0))
        assertEquals(0, plan.tileLevel)
        assertTrue(plan.desiredTilesByPage.getValue(0).isNotEmpty())
    }

    @Test
    fun wideLayout_whoseBaseIsCapped_tilesBeforeTheQualityStep() {
        // A 2000 px wide page, as on a tablet: the base is capped at 2048 px, so at zoom 1.2 it is
        // already blurrier than the screen. Uncapped, the same zoom would not tile (see below).
        val wide =
            layoutOf(floatArrayOf(2000f), floatArrayOf(1400f), 20f, ViewportMetrics(2000f, 2000f))
        val atOne =
            computer.compute(
                inputs(zoom = 1f, layout = wide, viewport = 2000f),
                0,
                never(),
                neverBase(),
            )
        val past =
            computer.compute(
                inputs(zoom = 1.2f, layout = wide, viewport = 2000f),
                0,
                never(),
                neverBase(),
            )

        assertNull("2048 px over 2000 is sharp enough", atOne.tileLevel)
        assertEquals(1, past.tileLevel)
        assertTrue(past.desiredTilesByPage.getValue(0).isNotEmpty())
    }

    @Test
    fun qualityBelowOne_isNotOverriddenByTilesAtZoomOne() {
        val plan = computer.compute(inputs(zoom = 1f, quality = 0.75f), 0, never(), neverBase())
        assertNull(plan.tileLevel)
    }

    @Test
    fun onlyThePagesThatNeedTiles_getThem() {
        // A normal page above a tall one: at zoom 1 only the tall one falls short.
        val mixed =
            layoutOf(
                floatArrayOf(500f, 500f),
                floatArrayOf(300f, 4000f),
                20f,
                ViewportMetrics(500f, 500f),
            )
        val plan = computer.compute(inputs(zoom = 1f, layout = mixed), 0, never(), neverBase())

        assertFalse(plan.desiredTilesByPage.containsKey(0))
        assertTrue(plan.desiredTilesByPage.getValue(1).isNotEmpty())
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

    /**
     * [layout] seen through a square [viewport], 500 px by default, from the top of the document.
     */
    private fun inputs(
        zoom: Float,
        velocityY: Float = 0f,
        layout: PageLayoutSnapshot = threePages(),
        viewport: Float = 500f,
        quality: Float = 1.5f,
    ): PlanInputs =
        PlanInputs(
            layout = layout,
            panX = 0f,
            panY = 0f,
            zoom = zoom,
            viewportWidth = viewport,
            viewportHeight = viewport,
            velocityX = 0f,
            velocityY = velocityY,
            renderQuality = quality,
            prefetchDistance = 2,
        )
}
