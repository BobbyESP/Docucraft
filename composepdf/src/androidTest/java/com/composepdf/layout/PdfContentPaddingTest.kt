/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composepdf.PdfLayoutSpec
import com.composepdf.PdfSource
import com.composepdf.PdfViewer
import com.composepdf.PdfViewerState
import com.composepdf.rememberPdfViewerState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step b7 of `docs/architecture/08-pdfviewer-migration-plan.md`: `PdfLayoutSpec.contentPadding`,
 * with the real engine. Pages are fitted to the area inside the padding and start below it; the
 * geometry rules themselves are covered on the JVM in `PageLayoutSnapshotTest`.
 */
@RunWith(AndroidJUnit4::class)
class PdfContentPaddingTest {

    @get:Rule val rule = createComposeRule()

    private lateinit var state: PdfViewerState
    private lateinit var density: Density
    private var viewerSize = IntSize.Zero

    @Test
    fun theFirstPageStartsBelowTheTopPadding() {
        show(PaddingValues(top = 120.dp, bottom = 80.dp))

        rule.runOnIdle {
            val top = with(density) { 120.dp.toPx() }
            assertEquals(top, state.pageRectInViewer(0)!!.top, 1f)
        }
    }

    @Test
    fun pagesAreFittedToTheWidthInsideTheHorizontalPadding() {
        show(PaddingValues(horizontal = 40.dp))

        rule.runOnIdle {
            val expected = viewerSize.width - with(density) { 80.dp.toPx() }
            val page = state.pageRectInViewer(0)!!
            assertEquals(expected, page.width, 1f)
            assertEquals(with(density) { 40.dp.toPx() }, page.left, 1f)
        }
    }

    /** No padding is exactly what the viewer did before: the page fills the width from the top. */
    @Test
    fun withoutPaddingNothingChanges() {
        show(PaddingValues(0.dp))

        rule.runOnIdle {
            val page = state.pageRectInViewer(0)!!
            assertEquals(0f, page.top, 1f)
            assertEquals(viewerSize.width.toFloat(), page.width, 1f)
        }
    }

    private fun show(padding: PaddingValues) {
        rule.setContent {
            density = LocalDensity.current
            state = rememberPdfViewerState()
            PdfViewer(
                source = PdfSource.Asset("fixtures/long-320-pages.pdf"),
                state = state,
                layout = PdfLayoutSpec(contentPadding = padding),
                modifier = Modifier.fillMaxSize().onSizeChanged { viewerSize = it },
            )
        }
        rule.waitUntil(timeoutMillis = 15_000) { state.isLoaded }
        rule.waitForIdle()
    }
}
