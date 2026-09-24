/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.state

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composepdf.PdfSource
import com.composepdf.PdfViewer
import com.composepdf.PdfViewerState
import com.composepdf.rememberPdfViewerState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B1 in `docs/architecture/06-pdfviewer-analysis.md`: `rememberPdfViewerState` saves page, zoom and
 * pan, but the reload that follows a recreation calls `state.reset()` and throws them away, so a
 * rotation or a process death lands the user back on page 1.
 *
 * Red until step b1 of the migration plan.
 */
@RunWith(AndroidJUnit4::class)
class PdfViewerStateRestorationTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun restoredPageSurvivesTheDocumentReload() {
        val restoration = StateRestorationTester(rule)
        lateinit var state: PdfViewerState

        restoration.setContent {
            state = rememberPdfViewerState()
            PdfViewer(
                source = PdfSource.Asset("fixtures/long-320-pages.pdf"),
                state = state,
                modifier = Modifier.fillMaxSize(),
            )
        }
        awaitLoaded { state }

        rule.runOnIdle { state.scrollToPage(TARGET_PAGE) }
        rule.runOnIdle { assertEquals(TARGET_PAGE, state.currentPage) }

        restoration.emulateSavedInstanceStateRestore()
        awaitLoaded { state }

        rule.runOnIdle { assertEquals(TARGET_PAGE, state.currentPage) }
    }

    private fun awaitLoaded(state: () -> PdfViewerState) {
        rule.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) { state().isLoaded }
        rule.waitForIdle()
    }

    private companion object {
        const val TARGET_PAGE = 5
        const val LOAD_TIMEOUT_MS = 15_000L
    }
}
