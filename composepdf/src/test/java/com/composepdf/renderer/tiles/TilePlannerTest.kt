package com.composepdf.renderer.tiles

import android.graphics.Rect
import android.util.Size
import com.composepdf.layout.PageLayoutSnapshot
import com.composepdf.layout.ViewportMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TilePlannerTest {

    private val planner = TilePlanner(tileSize = 256)

    @Test
    fun computeSteppedZoom_roundsToStableZoomBuckets() {
        assertEquals(1.25f, planner.computeSteppedZoom(1.26f), 0.001f)
        assertEquals(1.77f, planner.computeSteppedZoom(1.80f), 0.001f)
        assertEquals(2.5f, planner.computeSteppedZoom(2.55f), 0.001f)
    }

    @Test
    fun computeTilePlan_prioritizesVisibleTilesAndAddsPrefetchPage() {
        val layout = layoutSnapshot(
            pageCount = 3,
            pageTops = floatArrayOf(0f, 270f, 540f),
            pageHeights = floatArrayOf(250f, 250f, 250f),
            pageWidths = floatArrayOf(250f, 250f, 250f),
            totalDocumentHeight = 790f,
            maxPageWidth = 250f,
            viewportWidth = 500f,
            viewportHeight = 500f,
            pageSpacingPx = 20f
        )

        val plan = planner.computeTilePlan(
            viewport = ViewportState(
                width = 500f,
                height = 500f,
                panX = 0f,
                panY = -540f
            ),
            layout = layout,
            zoom = 2f,
            visiblePages = 1..1,
            scrollDirectionHint = 1,
            isTileCached = { false }
        )

        assertEquals(1.77f, plan.steppedZoom, 0.001f)
        assertEquals(listOf(2), plan.prefetchPages)
        assertTrue(plan.keepKeys.any { it.startsWith("1_") })
        assertTrue(plan.keepKeys.any { it.startsWith("2_") })
        assertEquals(1, plan.requests.first().tileKey.pageIndex)
        assertTrue(plan.requests.any { it.tileKey.pageIndex == 2 })
    }

    @Test
    fun computeTilePlan_addsViewportHaloTilesForVisiblePage() {
        val layout = layoutSnapshot(
            pageCount = 1,
            pageTops = floatArrayOf(0f),
            pageHeights = floatArrayOf(600f),
            pageWidths = floatArrayOf(600f),
            totalDocumentHeight = 600f,
            maxPageWidth = 600f,
            viewportWidth = 200f,
            viewportHeight = 100f,
            pageSpacingPx = 0f
        )

        val plan = planner.computeTilePlan(
            viewport = ViewportState(
                width = 200f,
                height = 100f,
                panX = 0f,
                panY = 0f
            ),
            layout = layout,
            zoom = 2f,
            visiblePages = 0..0,
            scrollDirectionHint = 0,
            isTileCached = { false }
        )

        assertTrue(plan.keepKeys.isNotEmpty())
        assertTrue(plan.requests.isNotEmpty())
    }

    @Test
    fun computeTilePlan_skipsAlreadyCachedTiles() {
        val layout = layoutSnapshot(
            pageCount = 1,
            pageTops = floatArrayOf(0f),
            pageHeights = floatArrayOf(250f),
            pageWidths = floatArrayOf(250f),
            totalDocumentHeight = 250f,
            maxPageWidth = 250f,
            viewportWidth = 500f,
            viewportHeight = 500f,
            pageSpacingPx = 0f
        )

        val cachedKey = TileKey(
            pageIndex = 0,
            rect = Rect(0, 0, 256, 256),
            zoom = 1.25f
        ).toCacheKey()

        val plan = planner.computeTilePlan(
            viewport = ViewportState(
                width = 500f,
                height = 500f,
                panX = 0f,
                panY = 0f
            ),
            layout = layout,
            zoom = 1.3f,
            visiblePages = 0..0,
            scrollDirectionHint = 0,
            isTileCached = { it == cachedKey }
        )

        assertTrue(plan.keepKeys.contains(cachedKey))
        assertTrue(plan.requests.none { it.tileKey.toCacheKey() == cachedKey })
    }

    private fun layoutSnapshot(
        pageCount: Int,
        pageTops: FloatArray,
        pageHeights: FloatArray,
        pageWidths: FloatArray,
        totalDocumentHeight: Float,
        maxPageWidth: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        pageSpacingPx: Float
    ) = PageLayoutSnapshot(
        pageSizes = List(pageCount) { Size(1, 1) },
        pageTops = pageTops,
        pageHeights = pageHeights,
        pageWidths = pageWidths,
        totalDocumentHeight = totalDocumentHeight,
        maxPageWidth = maxPageWidth,
        viewport = ViewportMetrics(viewportWidth, viewportHeight),
        pageSpacingPx = pageSpacingPx
    )
}
