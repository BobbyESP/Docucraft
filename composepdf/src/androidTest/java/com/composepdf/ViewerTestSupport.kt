/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf

import androidx.compose.ui.test.junit4.ComposeContentTestRule

/** 320 pages: room to scroll, zoom and jump without reaching either end by accident. */
internal val LongDocument = PdfSource.Asset("fixtures/long-320-pages.pdf")

/** Waits until the viewer has opened its document and settled. */
internal fun ComposeContentTestRule.waitUntilLoaded(state: () -> PdfViewerState) {
    waitUntil(timeoutMillis = 15_000) { state().isLoaded }
    waitForIdle()
}
