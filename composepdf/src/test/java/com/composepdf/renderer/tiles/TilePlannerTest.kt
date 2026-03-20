package com.composepdf.renderer.tiles

import android.graphics.Rect
import android.util.Size
import com.composepdf.internal.logic.PageLayoutSnapshot
import com.composepdf.internal.logic.ViewportMetrics
import com.composepdf.ScrollDirection
import com.composepdf.internal.logic.tiles.TileKey
import com.composepdf.internal.logic.tiles.TilePlanner
import com.composepdf.internal.logic.tiles.ViewportState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TilePlannerTest {

    private val planner = TilePlanner(tileSize = 256)

    @Test
    fun computeSteppedZoom_roundsToStableZoomBuckets() {
        assertEquals(1.0f, planner.computeSteppedZoom(1.26f), 0.001f)
        assertEquals(1.41f, planner.computeSteppedZoom(1.80f), 0.001f)
        assertEquals(2.0f, planner.computeSteppedZoom(2.55f), 0.001f)
    }

    @Test
    fun computeTilePlan_prioritizesVisibleTilesAndAddsPrefetchPage() {
        val layout = layoutSnapshot(
            pageCount = 3,
            pageOffsets = floatArrayOf(0f, 270f, 540f),
            pageHeights = floatArrayOf(250f, 250f, 250f),
            pageWidths = floatArrayOf(250f, 250f, 250f),
            totalDocumentSize = 790f,
            corridorBreadth = 250f,
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

        assertEquals(planner.computeSteppedZoom(2f), plan.steppedZoom, 0.001f)
        assertEquals(listOf(2), plan.prefetchPages)
        assertTrue(plan.keepKeys.any { it.startsWith("1_") })
        assertEquals(1, plan.requests.first().tileKey.pageIndex)
        assertTrue(plan.requests.all { it.tileKey.pageIndex == 1 })
    }

    @Test
    fun computeTilePlan_addsViewportHaloTilesForVisiblePage() {
        val layout = layoutSnapshot(
            pageCount = 1,
            pageOffsets = floatArrayOf(0f),
            pageHeights = floatArrayOf(600f),
            pageWidths = floatArrayOf(600f),
            totalDocumentSize = 600f,
            corridorBreadth = 600f,
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
            pageOffsets = floatArrayOf(0f),
            pageHeights = floatArrayOf(250f),
            pageWidths = floatArrayOf(250f),
            totalDocumentSize = 250f,
            corridorBreadth = 250f,
            viewportWidth = 500f,
            viewportHeight = 500f,
            pageSpacingPx = 0f
        )

        val cachedKey = TileKey.fromLayout(
            pageIndex = 0,
            rect = Rect(0, 0, 250, 250),
            zoom = 1.0f,
            baseWidth = 250f
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

    @Test
    fun computeTilePlan_includesBasePageWidthInKeepKeys() {
        val layoutNarrow = layoutSnapshot(
            pageCount = 1,
            pageOffsets = floatArrayOf(0f),
            pageHeights = floatArrayOf(250f),
            pageWidths = floatArrayOf(250f),
            totalDocumentSize = 250f,
            corridorBreadth = 250f,
            viewportWidth = 500f,
            viewportHeight = 500f,
            pageSpacingPx = 0f
        )
        val layoutWide = layoutSnapshot(
            pageCount = 1,
            pageOffsets = floatArrayOf(0f),
            pageHeights = floatArrayOf(250f),
            pageWidths = floatArrayOf(300f),
            totalDocumentSize = 250f,
            corridorBreadth = 300f,
            viewportWidth = 500f,
            viewportHeight = 500f,
            pageSpacingPx = 0f
        )

        val narrowPlan = planner.computeTilePlan(
            viewport = ViewportState(500f, 500f, 0f, 0f),
            layout = layoutNarrow,
            zoom = 1.3f,
            visiblePages = 0..0,
            scrollDirectionHint = 0,
            isTileCached = { false }
        )
        val widePlan = planner.computeTilePlan(
            viewport = ViewportState(500f, 500f, 0f, 0f),
            layout = layoutWide,
            zoom = 1.3f,
            visiblePages = 0..0,
            scrollDirectionHint = 0,
            isTileCached = { false }
        )

        assertTrue(narrowPlan.keepKeys.isNotEmpty())
        assertTrue(widePlan.keepKeys.isNotEmpty())
        assertTrue(narrowPlan.keepKeys.intersect(widePlan.keepKeys).isEmpty())
    }

    private fun layoutSnapshot(
        pageCount: Int,
        pageOffsets: FloatArray,
        pageHeights: FloatArray,
        pageWidths: FloatArray,
        totalDocumentSize: Float,
        corridorBreadth: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        pageSpacingPx: Float,
        scrollDirection: ScrollDirection = ScrollDirection.VERTICAL
    ) = PageLayoutSnapshot(
        pageSizes = List(pageCount) { Size(1, 1) },
        pageOffsets = pageOffsets,
        pageHeights = pageHeights,
        pageWidths = pageWidths,
        totalDocumentSize = totalDocumentSize,
        corridorBreadth = corridorBreadth,
        viewport = ViewportMetrics(viewportWidth, viewportHeight),
        pageSpacingPx = pageSpacingPx,
        scrollDirection = scrollDirection
    )
}
