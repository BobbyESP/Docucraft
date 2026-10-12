/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A widget is pressed while the app is not running, so what it asks for is asked before anybody can
 * act on it. The request has to wait for the shell, reach it once, and be the last thing the user
 * asked for.
 */
class LibraryEntryRequestsTest {

    @Test
    fun `nothing is pending to begin with`() {
        assertNull(LibraryEntryRequests().pending.value)
    }

    @Test
    fun `a request stands until somebody takes it`() {
        val requests = LibraryEntryRequests()

        requests.request(LibraryEntryRequest.Search)

        assertEquals(LibraryEntryRequest.Search, requests.pending.value)
    }

    @Test
    fun `a request is taken once`() {
        val requests = LibraryEntryRequests()
        requests.request(LibraryEntryRequest.OpenDocument("a"))

        assertEquals(LibraryEntryRequest.OpenDocument("a"), requests.take())
        assertNull("Taking it again would open the document twice", requests.take())
        assertNull(requests.pending.value)
    }

    /** Two rows pressed before the app catches up: the user meant the second. */
    @Test
    fun `asking for somewhere else replaces what was asked before`() {
        val requests = LibraryEntryRequests()

        requests.request(LibraryEntryRequest.OpenDocument("a"))
        requests.request(LibraryEntryRequest.OpenDocument("b"))

        assertEquals(LibraryEntryRequest.OpenDocument("b"), requests.take())
    }
}
