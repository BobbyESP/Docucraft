/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.layout

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composepdf.FitMode
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
 * The reading position across a change of page layout *without* a recreation: the viewer being
 * resized (a rotation in an activity that handles it, a foldable, multi-window, a list-detail pane)
 * or its fit mode changing. Pan is kept in pixels, and pixels mean another page once pages change
 * size — found when rotating the external viewer on page 200 of 320 landed on page 91.
 */
@RunWith(AndroidJUnit4::class)
class PdfLayoutChangeTest {

    @get:Rule val rule = createComposeRule()

    private lateinit var state: PdfViewerState
    private var width by mutableStateOf(400.dp)
    private var fitMode by mutableStateOf(FitMode.WIDTH)

    @Test
    fun resizingTheViewerKeepsThePage() {
        show()
        rule.runOnIdle { state.scrollToPage(TARGET) }
        rule.runOnIdle { assertEquals(TARGET, state.currentPage) }

        width = 250.dp
        rule.waitForIdle()

        rule.runOnIdle { assertEquals(TARGET, state.currentPage) }
    }

    @Test
    fun changingTheFitModeKeepsThePage() {
        show()
        rule.runOnIdle { state.scrollToPage(TARGET) }

        fitMode = FitMode.HEIGHT
        rule.waitForIdle()

        rule.runOnIdle { assertEquals(TARGET, state.currentPage) }
    }

    private fun show() {
        rule.setContent {
            state = rememberPdfViewerState()
            PdfViewer(
                source = PdfSource.Asset("fixtures/long-320-pages.pdf"),
                state = state,
                layout = PdfLayoutSpec(fitMode = fitMode),
                modifier = Modifier.width(width).fillMaxHeight(),
            )
        }
        rule.waitUntil(timeoutMillis = 15_000) { state.isLoaded }
        rule.waitForIdle()
    }

    private companion object {
        const val TARGET = 150
    }
}
