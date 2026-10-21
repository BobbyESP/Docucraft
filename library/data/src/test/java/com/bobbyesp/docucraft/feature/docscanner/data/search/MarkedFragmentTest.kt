/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.search

import com.bobbyesp.docucraft.feature.docscanner.domain.search.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkedFragmentTest {

    private val s = MarkedFragment.START
    private val e = MarkedFragment.END

    @Test
    fun `the markers are taken out and say where the match is`() {
        val (text, highlights) = MarkedFragment.parse("…total de la ${s}factura${e}: 42,10 €…")

        assertEquals("…total de la factura: 42,10 €…", text)
        assertEquals(listOf("factura"), highlights.map { text.substring(it.start, it.end) })
    }

    @Test
    fun `every match of the fragment is marked`() {
        val (text, highlights) = MarkedFragment.parse("${s}luz${e} y ${s}gas${e} de ${s}marzo${e}")

        assertEquals("luz y gas de marzo", text)
        assertEquals(
            listOf(TextRange(0, 3), TextRange(6, 9), TextRange(13, 18)),
            highlights,
        )
    }

    // A document has brackets and asterisks of its own. They are text, not marks.
    @Test
    fun `what looks like a mark in the text is kept as text`() {
        val (text, highlights) = MarkedFragment.parse("[see] *${s}note${e}* [1]")

        assertEquals("[see] *note* [1]", text)
        assertEquals(listOf("note"), highlights.map { text.substring(it.start, it.end) })
    }

    @Test
    fun `a fragment without matches has no highlights`() {
        assertEquals("plain text" to emptyList<TextRange>(), MarkedFragment.parse("plain text"))
    }

    @Test
    fun `a marker without its pair marks nothing`() {
        assertEquals("ab" to emptyList<TextRange>(), MarkedFragment.parse("a${e}b"))
        assertEquals("ab" to emptyList<TextRange>(), MarkedFragment.parse("a${s}b"))
        assertEquals("ab" to emptyList<TextRange>(), MarkedFragment.parse("a${s}${e}b"))
    }
}
