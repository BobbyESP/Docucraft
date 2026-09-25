/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.data

import com.bobbyesp.docucraft.feature.pdfviewer.data.content.WordSpan
import com.bobbyesp.docucraft.feature.pdfviewer.data.content.splitPageText
import org.junit.Assert.assertEquals
import org.junit.Test

class PageTextSplitterTest {

    @Test
    fun `lines split at the platform's line breaks and words at spaces`() {
        val text = "Hello  world\r\nsecond line"
        assertEquals(
            listOf(listOf("Hello", "world"), listOf("second", "line")),
            splitPageText(text).texts(),
        )
    }

    @Test
    fun `each word keeps its place in the page text, line breaks counted`() {
        val text = "ab cd\r\nef"
        val spans = splitPageText(text).flatten()

        assertEquals(
            listOf(WordSpan("ab", 0, 2), WordSpan("cd", 3, 5), WordSpan("ef", 7, 9)),
            spans,
        )
        spans.forEach { assertEquals(it.text, text.substring(it.start, it.endExclusive)) }
    }

    @Test
    fun `a blank line between paragraphs stays, trailing ones go`() {
        assertEquals(
            listOf(listOf("one"), emptyList(), listOf("two")),
            splitPageText("one\r\n\r\ntwo\r\n\r\n").texts(),
        )
    }

    @Test
    fun `lone line feeds and carriage returns also break lines`() {
        assertEquals(
            listOf(listOf("a"), listOf("b"), listOf("c")),
            splitPageText("a\nb\rc").texts(),
        )
    }

    @Test
    fun `blank text has no lines`() {
        assertEquals(emptyList<List<WordSpan>>(), splitPageText(""))
        assertEquals(emptyList<List<WordSpan>>(), splitPageText(" \r\n "))
    }

    private fun List<List<WordSpan>>.texts() = map { line -> line.map { it.text } }
}
