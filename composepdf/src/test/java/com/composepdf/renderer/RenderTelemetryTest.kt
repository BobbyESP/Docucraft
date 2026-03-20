package com.composepdf.renderer

import com.composepdf.internal.service.renderer.RenderTelemetry
import com.composepdf.internal.service.renderer.RenderTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderTelemetryTest {

    @Test
    fun telemetry_accumulatesPassSnapshotAndRecentEvents() {
        val telemetry = RenderTelemetry(maxEvents = 10)

        telemetry.recordPassStarted(
            passId = 7,
            trigger = RenderTrigger.GESTURE_DEBOUNCED,
            zoom = 2f,
            visiblePages = 3..4
        )
        telemetry.recordTilePlan(
            passId = 7,
            steppedZoom = 1.77f,
            keepTileCount = 12,
            requestCount = 5,
            prefetchPages = listOf(5)
        )
        telemetry.recordTileDiskHit(7, "tile-a")
        telemetry.recordTilePublished(7, "tile-a", stale = false)
        telemetry.recordActiveJobs(pageJobs = 2, tileJobs = 3)

        val snapshot = telemetry.snapshot.value
        val events = telemetry.recentEvents()

        assertEquals(7, snapshot.lastPassId)
        assertEquals(RenderTrigger.GESTURE_DEBOUNCED, snapshot.lastTrigger)
        assertEquals(1.77f, snapshot.lastSteppedZoom, 0.001f)
        assertEquals(12, snapshot.lastKeepTileCount)
        assertEquals(5, snapshot.lastRequestedTiles)
        assertEquals(1, snapshot.tileDiskHits)
        assertEquals(1, snapshot.tilesPublished)
        assertEquals(2, snapshot.activePageJobs)
        assertEquals(3, snapshot.activeTileJobs)
        assertTrue(events.any { it.action == "tile_plan" })
        assertTrue(events.any { it.action == "published" && it.source == "tile" })
    }
}

