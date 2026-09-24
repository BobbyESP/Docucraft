/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation

import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.ViewerChromeState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerChromeStateTest {

    private val chrome = ViewerChromeState(initiallyVisible = true, thresholdPx = 60f)

    @Test
    fun readingForwardPastTheThresholdHidesTheBars() {
        chrome.onScrolled(-30f)
        assertTrue("not yet", chrome.isVisible)

        chrome.onScrolled(-40f)
        assertFalse(chrome.isVisible)
    }

    @Test
    fun scrollingBackShowsThemAgain() {
        chrome.onScrolled(-100f)

        chrome.onScrolled(70f)

        assertTrue(chrome.isVisible)
    }

    /** A small wobble back and forth must not make the bars flicker. */
    @Test
    fun changingDirectionStartsTheCountAgain() {
        chrome.onScrolled(-50f)
        chrome.onScrolled(5f)
        chrome.onScrolled(-50f)

        assertTrue("50 + 50 in one direction, but with a reversal in between", chrome.isVisible)
    }

    @Test
    fun aTapTogglesTheBars() {
        chrome.toggle()
        assertFalse(chrome.isVisible)

        chrome.toggle()
        assertTrue(chrome.isVisible)
    }

    @Test
    fun aTapAfterHidingByScrollShowsThem() {
        chrome.onScrolled(-100f)

        chrome.toggle()

        assertTrue(chrome.isVisible)
    }
}
