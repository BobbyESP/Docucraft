package com.composepdf.layout

import android.util.Size
import com.composepdf.FitMode
import com.composepdf.ScrollDirection
import com.composepdf.internal.logic.PageLayoutSnapshot
import com.composepdf.internal.logic.ViewportMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageLayoutSnapshotTest {

    @Test
    fun visiblePageIndices_returnsIntersectingPagesAroundViewport() {
        val snapshot = snapshot(
            pageCount = 3,
            pageOffsets = floatArrayOf(0f, 520f, 1040f),
            pageHeights = floatArrayOf(500f, 500f, 500f),
            pageWidths = floatArrayOf(500f, 500f, 500f),
            totalDocumentSize = 1540f,
            corridorBreadth = 500f,
            viewportWidth = 500f,
            viewportHeight = 500f,
            pageSpacingPx = 20f
        )

        val visible = snapshot.visiblePageIndices(panX = 0f, panY = -520f, zoom = 1f)

        assertEquals(1..1, visible)
    }

    @Test
    fun clampPan_centersContentWhenDocumentIsSmallerThanViewport() {
        val snapshot = snapshot(
            pageCount = 1,
            pageOffsets = floatArrayOf(0f),
            pageHeights = floatArrayOf(800f),
            pageWidths = floatArrayOf(800f),
            totalDocumentSize = 800f,
            corridorBreadth = 800f,
            viewportWidth = 800f,
            viewportHeight = 1200f,
            pageSpacingPx = 0f
        )

        val clamped = snapshot.clampPan(panX = -50f, panY = -100f, zoom = 1f)

        assertEquals(0f, clamped.x, 0.001f)
        assertEquals(200f, clamped.y, 0.001f)
    }

    @Test
    fun centeredPanForPage_usesViewportCenterAndDocumentCorridor() {
        val snapshot = snapshot(
            pageCount = 2,
            pageOffsets = floatArrayOf(0f, 520f),
            pageHeights = floatArrayOf(500f, 500f),
            pageWidths = floatArrayOf(400f, 300f),
            totalDocumentSize = 1020f,
            corridorBreadth = 400f,
            viewportWidth = 600f,
            viewportHeight = 800f,
            pageSpacingPx = 20f
        )

        val centered = snapshot.centeredPanForPage(pageIndex = 1, zoom = 1f)

        assertEquals(100f, centered.x, 0.001f)
        assertEquals(-370f, centered.y, 0.001f)
    }

    @Test
    fun fitDocumentZoom_inHeightMode_usesTotalDocumentHeight() {
        val snapshot = snapshot(
            pageCount = 2,
            pageOffsets = floatArrayOf(0f, 550f),
            pageHeights = floatArrayOf(500f, 500f),
            pageWidths = floatArrayOf(500f, 500f),
            totalDocumentSize = 1050f,
            corridorBreadth = 500f,
            viewportWidth = 500f,
            viewportHeight = 500f,
            pageSpacingPx = 50f
        )

        val fitZoom = snapshot.fitDocumentZoom(
            fitMode = FitMode.HEIGHT,
            minZoom = 0.1f,
            maxZoom = 5f
        )

        assertTrue(fitZoom < 1f)
        assertEquals(500f / 1050f, fitZoom, 0.001f)
    }

    private fun snapshot(
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
