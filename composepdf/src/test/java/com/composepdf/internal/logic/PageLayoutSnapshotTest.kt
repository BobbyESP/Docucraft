/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.logic

import androidx.compose.ui.geometry.Offset
import com.composepdf.FitMode
import com.composepdf.ScrollDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageLayoutSnapshotTest {

    @Test
    fun visiblePageIndices_returnsIntersectingPagesAroundViewport() {
        val visible = threePages().visiblePageIndices(panX = 0f, panY = -520f, zoom = 1f)

        assertEquals(1..1, visible)
    }

    @Test
    fun clampPan_centersContentWhenDocumentIsSmallerThanViewport() {
        val snapshot =
            layoutOf(
                widths = floatArrayOf(800f),
                heights = floatArrayOf(800f),
                spacing = 0f,
                viewport = ViewportMetrics(800f, 1200f),
            )

        val clamped = snapshot.clampPan(panX = -50f, panY = -100f, zoom = 1f)

        assertEquals(0f, clamped.x, 0.001f)
        assertEquals(200f, clamped.y, 0.001f)
    }

    @Test
    fun centeredPanForPage_usesViewportCenterAndDocumentCorridor() {
        val snapshot =
            layoutOf(
                widths = floatArrayOf(400f, 300f),
                heights = floatArrayOf(500f, 500f),
                spacing = 20f,
                viewport = ViewportMetrics(600f, 800f),
            )

        val centered = snapshot.centeredPanForPage(pageIndex = 1, zoom = 1f)

        assertEquals(100f, centered.x, 0.001f)
        assertEquals(-370f, centered.y, 0.001f)
    }

    @Test
    fun fitDocumentZoom_inHeightMode_usesTotalDocumentHeight() {
        val snapshot =
            layoutOf(
                widths = floatArrayOf(500f, 500f),
                heights = floatArrayOf(500f, 500f),
                spacing = 50f,
                viewport = ViewportMetrics(500f, 500f),
            )

        val fitZoom =
            snapshot.fitDocumentZoom(fitMode = FitMode.HEIGHT, minZoom = 0.1f, maxZoom = 5f)

        assertTrue(fitZoom < 1f)
        assertEquals(500f / 1050f, fitZoom, 0.001f)
    }

    // ------------------------------------------------------------------ anchors

    @Test
    fun anchorAtContentCenter_findsThePageAndHowFarIntoItTheCentreFalls() {
        // Centre at 250 px on screen: document offset 250 + 520 = 770, halfway down page 1.
        val anchor = threePages().anchorAtContentCenter(panX = 0f, panY = -520f, zoom = 1f)!!

        assertEquals(1, anchor.pageIndex)
        assertEquals(0.5f, anchor.fraction, 0.001f)
    }

    @Test
    fun anchorAtContentCenter_isIndependentOfZoom() {
        // (250 - pan) / 2 = 770.
        val anchor = threePages().anchorAtContentCenter(panX = 0f, panY = 250f - 1540f, zoom = 2f)!!

        assertEquals(1, anchor.pageIndex)
        assertEquals(0.5f, anchor.fraction, 0.001f)
    }

    @Test
    fun anchorAtContentCenter_beforeTheFirstPage_isItsLeadingEdge() {
        val anchor = threePages().anchorAtContentCenter(panX = 0f, panY = 400f, zoom = 1f)!!

        assertEquals(0, anchor.pageIndex)
        assertEquals(0f, anchor.fraction, 0.001f)
    }

    @Test
    fun panForAnchor_putsTheAnchorAtTheCentreAndCentresTheCorridor() {
        val snapshot = threePages(ViewportMetrics(700f, 500f))

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
        val portrait = threePages(ViewportMetrics(500f, 800f))
        val landscape =
            layoutOf(
                widths = FloatArray(3) { 1500f },
                heights = FloatArray(3) { 1500f },
                spacing = 30f,
                viewport = ViewportMetrics(1500f, 700f),
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
            layoutOf(
                widths = floatArrayOf(400f, 400f),
                heights = floatArrayOf(600f, 600f),
                spacing = 20f,
                viewport = ViewportMetrics(400f, 800f),
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

    // ------------------------------------------------------------------ content padding (E6)

    /** Bars 100 px tall above and 80 px below: 620 px of content area in an 800 px viewport. */
    private val withBars =
        threePages(ViewportMetrics(500f, 800f, ContentPaddingPx(top = 100f, bottom = 80f)))

    @Test
    fun clampPan_withPadding_stopsTheFirstPageBelowTheTopBar() {
        val clamped = withBars.clampPan(panX = 0f, panY = 500f, zoom = 1f)

        assertEquals(100f, clamped.y, 0.001f)
    }

    @Test
    fun clampPan_withPadding_stopsTheLastPageAboveTheBottomBar() {
        val clamped = withBars.clampPan(panX = 0f, panY = -5_000f, zoom = 1f)

        // The document's bottom edge (1540 px) lands at 800 - 80.
        assertEquals(720f - 1540f, clamped.y, 0.001f)
    }

    @Test
    fun clampPan_withPadding_centresASmallDocumentInTheContentArea() {
        val snapshot =
            layoutOf(
                widths = floatArrayOf(500f),
                heights = floatArrayOf(400f),
                spacing = 0f,
                viewport = ViewportMetrics(500f, 800f, ContentPaddingPx(top = 100f, bottom = 100f)),
            )

        val clamped = snapshot.clampPan(panX = 0f, panY = 0f, zoom = 1f)

        assertEquals(100f + (600f - 400f) / 2f, clamped.y, 0.001f)
    }

    @Test
    fun centeredPanForPage_withPadding_centresInTheContentArea() {
        val centered = withBars.centeredPanForPage(pageIndex = 1, zoom = 1f)

        // Centre of the content area: 100 + 620 / 2 = 410; centre of page 1: 520 + 250 = 770.
        assertEquals(410f - 770f, centered.y, 0.001f)
    }

    @Test
    fun anchor_withPadding_isTakenAtTheCentreOfTheContentArea() {
        val anchor = withBars.anchorAtContentCenter(panX = 0f, panY = 410f - 770f, zoom = 1f)!!
        val pan = withBars.panForAnchor(anchor, zoom = 1f)

        assertEquals(1, anchor.pageIndex)
        assertEquals(0.5f, anchor.fraction, 0.001f)
        assertEquals(410f - 770f, pan.y, 0.001f)
    }

    @Test
    fun currentPage_withPadding_isTheOneAtTheCentreOfTheContentArea() {
        // Content centre at 410 px on screen; with pan -150 that is document offset 560: page 1.
        assertEquals(1, withBars.currentPageAtViewportCenter(panX = 0f, panY = -150f, zoom = 1f))
    }

    // ------------------------------------------------------------------ a point on a page (E5)

    /** A 500 × 500 viewer whose top 100 px are under a bar. */
    private val underABar = threePages(ViewportMetrics(500f, 500f, ContentPaddingPx(top = 100f)))

    @Test
    fun panForPagePoint_bringsThePointToTheStartOfTheContentArea() {
        val pan = underABar.panForPagePoint(1, Offset(0.5f, 0.4f), panX = 0f, panY = 0f, zoom = 1f)

        // 40 % into page 1 is document y 720; it lands just below the bar, at 100.
        assertEquals(100f - 720f, pan.y, 0.001f)
        assertEquals("already on screen across, so left alone", 0f, pan.x, 0.001f)
    }

    @Test
    fun panForPagePoint_withoutAPositionBringsThePageStart() {
        val pan = underABar.panForPagePoint(2, position = null, panX = -30f, panY = 0f, zoom = 1f)

        assertEquals(100f - 1040f, pan.y, 0.001f)
        assertEquals(-30f, pan.x, 0.001f)
    }

    @Test
    fun panForPagePoint_centresAPointThatWouldBeOffScreenAcross() {
        // At zoom 2 the page is 1000 wide; 90 % across is document x 450, screen x 900.
        val pan = underABar.panForPagePoint(0, Offset(0.9f, 0f), panX = 0f, panY = 0f, zoom = 2f)

        assertEquals(250f - 900f, pan.x, 0.001f)
    }
}
