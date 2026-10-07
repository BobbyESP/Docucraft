/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrollHeadingTrackerTest {

    private val tracker = ScrollHeadingTracker(thresholdPx = 60f)

    @Test
    fun `a scroll has no heading until it has travelled the threshold`() {
        assertNull(tracker.onScrolled(-30f))
        assertEquals(ScrollHeading.Forward, tracker.onScrolled(-40f))
    }

    @Test
    fun `turning around takes the whole threshold again`() {
        tracker.onScrolled(-100f)

        assertNull("a small move back is not a change of mind", tracker.onScrolled(20f))
        assertEquals(ScrollHeading.Backward, tracker.onScrolled(50f))
    }

    @Test
    fun `a reversal starts the count again`() {
        tracker.onScrolled(-50f)
        tracker.onScrolled(5f)

        assertNull("50 + 50 one way, but with a reversal in between", tracker.onScrolled(-50f))
    }

    @Test
    fun `a scroll that moved nothing says nothing`() {
        tracker.onScrolled(-100f)

        assertNull(tracker.onScrolled(0f))
    }

    @Test
    fun `reset forgets the travel so far`() {
        tracker.onScrolled(-50f)
        tracker.reset()

        assertNull(tracker.onScrolled(-50f))
    }
}
