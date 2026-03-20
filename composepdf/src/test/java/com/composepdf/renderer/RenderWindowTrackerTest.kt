package com.composepdf.renderer

import com.composepdf.internal.service.cache.PageCacheKey
import com.composepdf.internal.service.renderer.RenderWindowTracker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderWindowTrackerTest {

    @Test
    fun shouldPublishPage_allowsOlderJobWhenOutputIsStillDesired() {
        val tracker = RenderWindowTracker()
        val session = tracker.beginNewSession()
        val pageKey = PageCacheKey(pageIndex = 2, zoomLevel = 1.5f, width = 800, height = 1200)

        tracker.updateDesiredPages(mapOf(2 to pageKey))

        assertTrue(tracker.shouldPublishPage(session, 2, pageKey))
    }

    @Test
    fun shouldPublishPage_rejectsResultsFromPreviousSession() {
        val tracker = RenderWindowTracker()
        val oldSession = tracker.beginNewSession()
        val pageKey = PageCacheKey(pageIndex = 0, zoomLevel = 1f, width = 400, height = 600)
        tracker.updateDesiredPages(mapOf(0 to pageKey))
        tracker.beginNewSession()
        tracker.updateDesiredPages(mapOf(0 to pageKey))

        assertFalse(tracker.shouldPublishPage(oldSession, 0, pageKey))
    }

    @Test
    fun shouldPublishTile_requiresTileToRemainInKeepWindow() {
        val tracker = RenderWindowTracker()
        val session = tracker.beginNewSession()
        val tileKey = "0_0_0_256_256_1.25_50000"

        tracker.updateDesiredTiles(setOf(tileKey))
        assertTrue(tracker.shouldPublishTile(session, tileKey))

        tracker.updateDesiredTiles(emptySet())
        assertFalse(tracker.shouldPublishTile(session, tileKey))
    }
}

