/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerChromeStateTest {

    private val chrome = ViewerChromeState(initiallyVisible = true, thresholdPx = 60f)

    @Test
    fun `reading forward past the threshold hides the bars`() {
        chrome.onScrolled(-30f)
        assertTrue("not yet", chrome.isVisible)

        chrome.onScrolled(-40f)
        assertFalse(chrome.isVisible)
    }

    @Test
    fun `scrolling back shows them again`() {
        chrome.onScrolled(-100f)

        chrome.onScrolled(70f)

        assertTrue(chrome.isVisible)
    }

    /** A small wobble back and forth must not make the bars flicker. */
    @Test
    fun `changing direction starts the count again`() {
        chrome.onScrolled(-50f)
        chrome.onScrolled(5f)
        chrome.onScrolled(-50f)

        assertTrue("50 + 50 in one direction, but with a reversal in between", chrome.isVisible)
    }

    @Test
    fun `a tap toggles the bars`() {
        chrome.toggle()
        assertFalse(chrome.isVisible)

        chrome.toggle()
        assertTrue(chrome.isVisible)
    }

    @Test
    fun `a tap after hiding by scroll shows them`() {
        chrome.onScrolled(-100f)

        chrome.toggle()

        assertTrue(chrome.isVisible)
    }
}
