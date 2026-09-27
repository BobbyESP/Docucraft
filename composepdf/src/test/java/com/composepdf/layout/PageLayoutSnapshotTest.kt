/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.layout

import android.util.Size
import androidx.compose.ui.geometry.Offset
import com.composepdf.FitMode
import com.composepdf.ScrollDirection
import com.composepdf.internal.logic.ContentPaddingPx
import com.composepdf.internal.logic.PageAnchor
import com.composepdf.internal.logic.PageLayoutSnapshot
import com.composepdf.internal.logic.ViewportMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageLayoutSnapshotTest {

    @Test
    fun visiblePageIndices_returnsIntersectingPagesAroundViewport() {
        val snapshot =
            snapshot(
                pageCount = 3,
                pageOffsets = floatArrayOf(0f, 520f, 1040f),
                pageHeights = floatArrayOf(500f, 500f, 500f),
                pageWidths = floatArrayOf(500f, 500f, 500f),
                totalDocumentSize = 1540f,
                corridorBreadth = 500f,
                viewportWidth = 500f,
                viewportHeight = 500f,
                pageSpacingPx = 20f,
            )

        val visible = snapshot.visiblePageIndices(panX = 0f, panY = -520f, zoom = 1f)

        assertEquals(1..1, visible)
    }

    @Test
    fun clampPan_centersContentWhenDocumentIsSmallerThanViewport() {
        val snapshot =
            snapshot(
                pageCount = 1,
                pageOffsets = floatArrayOf(0f),
                pageHeights = floatArrayOf(800f),
                pageWidths = floatArrayOf(800f),
                totalDocumentSize = 800f,
                corridorBreadth = 800f,
                viewportWidth = 800f,
                viewportHeight = 1200f,
                pageSpacingPx = 0f,
            )

        val clamped = snapshot.clampPan(panX = -50f, panY = -100f, zoom = 1f)

        assertEquals(0f, clamped.x, 0.001f)
        assertEquals(200f, clamped.y, 0.001f)
    }

    @Test
    fun centeredPanForPage_usesViewportCenterAndDocumentCorridor() {
        val snapshot =
            snapshot(
                pageCount = 2,
                pageOffsets = floatArrayOf(0f, 520f),
                pageHeights = floatArrayOf(500f, 500f),
                pageWidths = floatArrayOf(400f, 300f),
                totalDocumentSize = 1020f,
                corridorBreadth = 400f,
                viewportWidth = 600f,
                viewportHeight = 800f,
                pageSpacingPx = 20f,
            )

        val centered = snapshot.centeredPanForPage(pageIndex = 1, zoom = 1f)

        assertEquals(100f, centered.x, 0.001f)
        assertEquals(-370f, centered.y, 0.001f)
    }

    @Test
    fun anchorAtContentCenter_findsThePageAndHowFarIntoItTheCentreFalls() {
        val snapshot = threePages(viewportWidth = 500f, viewportHeight = 500f)

        // Centre at 250 px on screen: document offset 250 + 520 = 770, halfway down page 1.
        val anchor = snapshot.anchorAtContentCenter(panX = 0f, panY = -520f, zoom = 1f)!!

        assertEquals(1, anchor.pageIndex)
        assertEquals(0.5f, anchor.fraction, 0.001f)
    }

    @Test
    fun anchorAtContentCenter_isIndependentOfZoom() {
        val snapshot = threePages(viewportWidth = 500f, viewportHeight = 500f)

        // (250 - pan) / 2 = 770.
        val anchor = snapshot.anchorAtContentCenter(panX = 0f, panY = 250f - 1540f, zoom = 2f)!!

        assertEquals(1, anchor.pageIndex)
        assertEquals(0.5f, anchor.fraction, 0.001f)
    }

    @Test
    fun anchorAtContentCenter_beforeTheFirstPage_isItsLeadingEdge() {
        val snapshot = threePages(viewportWidth = 500f, viewportHeight = 500f)

        val anchor = snapshot.anchorAtContentCenter(panX = 0f, panY = 400f, zoom = 1f)!!

        assertEquals(0, anchor.pageIndex)
        assertEquals(0f, anchor.fraction, 0.001f)
    }

    @Test
    fun panForAnchor_putsTheAnchorAtTheCentreAndCentresTheCorridor() {
        val snapshot = threePages(viewportWidth = 700f, viewportHeight = 500f)

        val pan = snapshot.panForAnchor(PageAnchor(pageIndex = 2, fraction = 0.25f), zoom = 1f)

        assertEquals(100f, pan.x, 0.001f)
        assertEquals(250f - (1040f + 125f), pan.y, 0.001f)
    }

    /**
     * The reason anchors exist: a rotation brings a different viewport and therefore a different
     * layout, where the old pan would point somewhere else. The anchor comes back unchanged.
     */
    @Test
    fun anchor_survivesALayoutChange() {
        val portrait = threePages(viewportWidth = 500f, viewportHeight = 800f)
        val landscape =
            snapshot(
                pageCount = 3,
                pageOffsets = floatArrayOf(0f, 1530f, 3060f),
                pageHeights = floatArrayOf(1500f, 1500f, 1500f),
                pageWidths = floatArrayOf(1500f, 1500f, 1500f),
                totalDocumentSize = 4560f,
                corridorBreadth = 1500f,
                viewportWidth = 1500f,
                viewportHeight = 700f,
                pageSpacingPx = 30f,
            )
        // Centre at 400 px: document offset 650, on page 1.
        val anchor = portrait.anchorAtContentCenter(panX = 0f, panY = -250f, zoom = 1f)!!

        val pan = landscape.panForAnchor(anchor, zoom = 1f)
        val back = landscape.anchorAtContentCenter(pan.x, pan.y, zoom = 1f)!!

        assertEquals(anchor.pageIndex, back.pageIndex)
        assertEquals(anchor.fraction, back.fraction, 0.001f)
    }

    @Test
    fun anchor_followsTheScrollAxisWhenHorizontal() {
        val snapshot =
            snapshot(
                pageCount = 2,
                pageOffsets = floatArrayOf(0f, 420f),
                pageHeights = floatArrayOf(600f, 600f),
                pageWidths = floatArrayOf(400f, 400f),
                totalDocumentSize = 820f,
                corridorBreadth = 600f,
                viewportWidth = 400f,
                viewportHeight = 800f,
                pageSpacingPx = 20f,
                scrollDirection = ScrollDirection.HORIZONTAL,
            )

        // Centre at 200 px across: document offset 520, a quarter into page 1.
        val anchor = snapshot.anchorAtContentCenter(panX = -320f, panY = 0f, zoom = 1f)!!
        val pan = snapshot.panForAnchor(anchor, zoom = 1f)

        assertEquals(1, anchor.pageIndex)
        assertEquals(0.25f, anchor.fraction, 0.001f)
        assertEquals(-320f, pan.x, 0.001f)
        assertEquals(100f, pan.y, 0.001f)
    }

    // ------------------------------------------------------------------ content padding (step b7)

    /** Bars 100 px tall above and 80 px below: 620 px of content area in an 800 px viewport. */
    private val bars = ContentPaddingPx(top = 100f, bottom = 80f)

    @Test
    fun clampPan_withPadding_stopsTheFirstPageBelowTheTopBar() {
        val snapshot = threePages(viewportWidth = 500f, viewportHeight = 800f, padding = bars)

        val clamped = snapshot.clampPan(panX = 0f, panY = 500f, zoom = 1f)

        assertEquals(100f, clamped.y, 0.001f)
    }

    @Test
    fun clampPan_withPadding_stopsTheLastPageAboveTheBottomBar() {
        val snapshot = threePages(viewportWidth = 500f, viewportHeight = 800f, padding = bars)

        val clamped = snapshot.clampPan(panX = 0f, panY = -5_000f, zoom = 1f)

        // The document's bottom edge (1540 px) lands at 800 - 80.
        assertEquals(720f - 1540f, clamped.y, 0.001f)
    }

    @Test
    fun clampPan_withPadding_centresASmallDocumentInTheContentArea() {
        val snapshot =
            snapshot(
                pageCount = 1,
                pageOffsets = floatArrayOf(0f),
                pageHeights = floatArrayOf(400f),
                pageWidths = floatArrayOf(500f),
                totalDocumentSize = 400f,
                corridorBreadth = 500f,
                viewportWidth = 500f,
                viewportHeight = 800f,
                pageSpacingPx = 0f,
                padding = ContentPaddingPx(top = 100f, bottom = 100f),
            )

        val clamped = snapshot.clampPan(panX = 0f, panY = 0f, zoom = 1f)

        assertEquals(100f + (600f - 400f) / 2f, clamped.y, 0.001f)
    }

    @Test
    fun centeredPanForPage_withPadding_centresInTheContentArea() {
        val snapshot = threePages(viewportWidth = 500f, viewportHeight = 800f, padding = bars)

        val centered = snapshot.centeredPanForPage(pageIndex = 1, zoom = 1f)

        // Centre of the content area: 100 + 620 / 2 = 410; centre of page 1: 520 + 250 = 770.
        assertEquals(410f - 770f, centered.y, 0.001f)
    }

    @Test
    fun anchor_withPadding_isTakenAtTheCentreOfTheContentArea() {
        val snapshot = threePages(viewportWidth = 500f, viewportHeight = 800f, padding = bars)

        // Content centre at 100 + 620 / 2 = 410 px.
        val anchor = snapshot.anchorAtContentCenter(panX = 0f, panY = 410f - 770f, zoom = 1f)!!
        val pan = snapshot.panForAnchor(anchor, zoom = 1f)

        assertEquals(1, anchor.pageIndex)
        assertEquals(0.5f, anchor.fraction, 0.001f)
        assertEquals(410f - 770f, pan.y, 0.001f)
    }

    @Test
    fun currentPage_withPadding_isTheOneAtTheCentreOfTheContentArea() {
        val snapshot = threePages(viewportWidth = 500f, viewportHeight = 800f, padding = bars)

        // Content centre at 410 px on screen; with pan -150 that is document offset 560: page 1.
        assertEquals(1, snapshot.currentPageAtViewportCenter(panX = 0f, panY = -150f, zoom = 1f))
    }

    @Test
    fun fitDocumentZoom_inHeightMode_usesTotalDocumentHeight() {
        val snapshot =
            snapshot(
                pageCount = 2,
                pageOffsets = floatArrayOf(0f, 550f),
                pageHeights = floatArrayOf(500f, 500f),
                pageWidths = floatArrayOf(500f, 500f),
                totalDocumentSize = 1050f,
                corridorBreadth = 500f,
                viewportWidth = 500f,
                viewportHeight = 500f,
                pageSpacingPx = 50f,
            )

        val fitZoom =
            snapshot.fitDocumentZoom(fitMode = FitMode.HEIGHT, minZoom = 0.1f, maxZoom = 5f)

        assertTrue(fitZoom < 1f)
        assertEquals(500f / 1050f, fitZoom, 0.001f)
    }

    /** Three 500 × 500 pages, 20 px apart, stacked vertically. */
    private fun threePages(
        viewportWidth: Float,
        viewportHeight: Float,
        padding: ContentPaddingPx = ContentPaddingPx.Zero,
    ) =
        snapshot(
            pageCount = 3,
            pageOffsets = floatArrayOf(0f, 520f, 1040f),
            pageHeights = floatArrayOf(500f, 500f, 500f),
            pageWidths = floatArrayOf(500f, 500f, 500f),
            totalDocumentSize = 1540f,
            corridorBreadth = 500f,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            pageSpacingPx = 20f,
            padding = padding,
        )

    // ------------------------------------------------------------------ a point on a page (E5)

    /** Three 500×500 pages, 20 apart, in a 500×500 viewer whose top 100 px are under a bar. */
    private val linked =
        snapshot(
            pageCount = 3,
            pageOffsets = floatArrayOf(0f, 520f, 1040f),
            pageHeights = floatArrayOf(500f, 500f, 500f),
            pageWidths = floatArrayOf(500f, 500f, 500f),
            totalDocumentSize = 1540f,
            corridorBreadth = 500f,
            viewportWidth = 500f,
            viewportHeight = 500f,
            pageSpacingPx = 20f,
            padding = ContentPaddingPx(left = 0f, top = 100f, right = 0f, bottom = 0f),
        )

    @Test
    fun panForPagePoint_bringsThePointToTheStartOfTheContentArea() {
        val pan = linked.panForPagePoint(1, Offset(0.5f, 0.4f), panX = 0f, panY = 0f, zoom = 1f)

        // 40 % into page 1 is document y 720; it lands just below the bar, at 100.
        assertEquals(100f - 720f, pan.y, 0.001f)
        assertEquals("already on screen across, so left alone", 0f, pan.x, 0.001f)
    }

    @Test
    fun panForPagePoint_withoutAPositionBringsThePageStart() {
        val pan = linked.panForPagePoint(2, position = null, panX = -30f, panY = 0f, zoom = 1f)

        assertEquals(100f - 1040f, pan.y, 0.001f)
        assertEquals(-30f, pan.x, 0.001f)
    }

    @Test
    fun panForPagePoint_centresAPointThatWouldBeOffScreenAcross() {
        // At zoom 2 the page is 1000 wide; 90 % across is document x 450, screen x 900.
        val pan = linked.panForPagePoint(0, Offset(0.9f, 0f), panX = 0f, panY = 0f, zoom = 2f)

        assertEquals(250f - 900f, pan.x, 0.001f)
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
        scrollDirection: ScrollDirection = ScrollDirection.VERTICAL,
        padding: ContentPaddingPx = ContentPaddingPx.Zero,
    ) =
        PageLayoutSnapshot(
            pageSizes = List(pageCount) { Size(1, 1) },
            pageOffsets = pageOffsets,
            pageHeights = pageHeights,
            pageWidths = pageWidths,
            totalDocumentSize = totalDocumentSize,
            corridorBreadth = corridorBreadth,
            viewport = ViewportMetrics(viewportWidth, viewportHeight, padding),
            pageSpacingPx = pageSpacingPx,
            scrollDirection = scrollDirection,
        )
}
