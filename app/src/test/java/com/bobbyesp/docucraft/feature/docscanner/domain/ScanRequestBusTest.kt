/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A scan asked for from outside the UI has to reach the catalogue whatever the user was looking at
 * when they asked, and has to reach it exactly once.
 *
 * It used to be a one-shot channel, which got both halves wrong: only the first reader learnt of a
 * request, and a request made while the catalogue was off screen had no reader at all. It waited —
 * and then started the scanner unasked, later, the moment the catalogue came back.
 */
class ScanRequestBusTest {

    @Test
    fun `nothing is pending to begin with`() {
        assertFalse(ScanRequestBus().isPending.value)
    }

    /** The whole point: a request outlives there being nobody to act on it yet. */
    @Test
    fun `a request stands until somebody takes it`() {
        val bus = ScanRequestBus()

        bus.request()

        assertTrue("A request nobody has taken is still pending", bus.isPending.value)
        assertTrue(bus.take())
    }

    @Test
    fun `taking a request clears it`() {
        val bus = ScanRequestBus()
        bus.request()

        bus.take()

        assertFalse(bus.isPending.value)
    }

    /**
     * Two observers watch this — the shell, to put the catalogue back on screen, and the catalogue,
     * to run the scan — so losing the race has to be something a caller can see.
     */
    @Test
    fun `only one caller can take a request`() {
        val bus = ScanRequestBus()
        bus.request()

        assertTrue(bus.take())
        assertFalse("The second taker must be told there was nothing left", bus.take())
    }

    @Test
    fun `taking when nothing was asked for does nothing`() {
        assertFalse(ScanRequestBus().take())
    }

    /** Pressing the widget twice before the app catches up is still one scan. */
    @Test
    fun `asking twice before anyone takes it is one request`() {
        val bus = ScanRequestBus()

        bus.request()
        bus.request()

        assertTrue(bus.take())
        assertFalse(bus.take())
    }
}
