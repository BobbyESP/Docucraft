/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.state

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composepdf.LongDocument
import com.composepdf.PdfViewer
import com.composepdf.PdfViewerState
import com.composepdf.rememberPdfViewerState
import com.composepdf.waitUntilLoaded
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The reading position survives recreation (E1, `docs/pdf-engine.md`). It used not to:
 * `rememberPdfViewerState` saved page, zoom and pan, but the reload that follows a recreation reset
 * them, so a rotation or a process death landed the user back on page 1.
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
                source = LongDocument,
                state = state,
                modifier = Modifier.fillMaxSize(),
            )
        }
        rule.waitUntilLoaded { state }

        rule.runOnIdle { state.scrollToPage(TARGET_PAGE) }
        rule.runOnIdle { assertEquals(TARGET_PAGE, state.currentPage) }

        restoration.emulateSavedInstanceStateRestore()
        rule.waitUntilLoaded { state }

        rule.runOnIdle { assertEquals(TARGET_PAGE, state.currentPage) }
    }

    private companion object {
        const val TARGET_PAGE = 5
    }
}
