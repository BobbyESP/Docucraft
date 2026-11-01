/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.documentcontent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSearchTest {

    /** The texts matched, cut from [text] by the spans found: what the reader would see lit. */
    private fun found(text: String, query: String): List<String> {
        val needle = TextSearch.needle(query) ?: return emptyList()
        return TextSearch.find(FoldedText.of(text), needle).map { text.substring(it.start, it.end) }
    }

    // ------------------------------------------------------------------ what counts as the same

    @Test
    fun `case does not matter`() {
        assertEquals(
            listOf("Invoice", "INVOICE", "invoice"),
            found("Invoice INVOICE invoice", "InVoIcE"),
        )
    }

    @Test
    fun `accents do not matter, in the page or in the query`() {
        assertEquals(listOf("Résumé"), found("Résumé", "resume"))
        assertEquals(listOf("resume"), found("resume", "RÉSUMÉ"))
        assertEquals(listOf("camión"), found("un camión rojo", "camion"))
    }

    @Test
    fun `an accent typed apart from its letter finds the same as the one-piece letter`() {
        assertEquals(listOf("é"), found("é", "é"))
        assertEquals(listOf("é"), found("é", "é"))
    }

    @Test
    fun `a ligature is the letters it stands for`() {
        // PDFs often hand over "ﬁ" as one character.
        assertEquals(listOf("ﬁle"), found("a ﬁle", "file"))
        assertEquals(listOf("ﬁle"), found("a ﬁle", "ﬁle"))
    }

    @Test
    fun `a match that ends inside a ligature takes the whole ligature`() {
        assertEquals(listOf("ﬁ"), found("ﬁ", "f"))
    }

    @Test
    fun `a line break, a paragraph break and a no-break space are one space`() {
        assertEquals(listOf("hello\nworld"), found("say hello\nworld", "hello world"))
        assertEquals(listOf("hello\n\nworld"), found("say hello\n\nworld", "hello world"))
        assertEquals(listOf("hello world"), found("say hello world", "hello world"))
        assertEquals(listOf("hello world"), found("say hello world", "hello   world"))
    }

    @Test
    fun `the invisible characters a broken word leaves are not part of it`() {
        // A soft hyphen where a word may be broken, and a zero-width space.
        assertEquals(listOf("inter­national"), found("inter­national", "international"))
        assertEquals(listOf("ab​cd"), found("ab​cd", "abcd"))
    }

    // ------------------------------------------------------------------ finding

    @Test
    fun `every match is found, in reading order`() {
        val text = "one two one\nthree one"
        val needle = TextSearch.needle("one")!!
        val spans = TextSearch.find(FoldedText.of(text), needle)

        assertEquals(listOf(TextSpan(0, 3), TextSpan(8, 11), TextSpan(18, 21)), spans)
    }

    @Test
    fun `matches are in the offsets of the original text, however folding moved things`() {
        // The collapsed space and the ligature both shift the folded text against the original.
        val text = "a  ﬁne\n\nline"
        val spans = TextSearch.find(FoldedText.of(text), TextSearch.needle("line")!!)

        assertEquals(listOf("line"), spans.map { text.substring(it.start, it.end) })
        assertEquals(TextSpan(8, 12), spans.single())
    }

    @Test
    fun `matches do not overlap`() {
        assertEquals(listOf("aa"), found("aaa", "aa"))
        assertEquals(2, found("aaaa", "aa").size)
    }

    @Test
    fun `a match is never cut inside a character outside the basic plane`() {
        val text = "x 😀 y"
        val spans = TextSearch.find(FoldedText.of(text), TextSearch.needle("😀")!!)

        assertEquals(listOf("😀"), spans.map { text.substring(it.start, it.end) })
    }

    @Test
    fun `nothing found is an empty list`() {
        assertTrue(found("alpha beta", "gamma").isEmpty())
    }

    @Test
    fun `part of a word is found`() {
        assertEquals(listOf("form"), found("information", "form"))
    }

    // ------------------------------------------------------------------ what is not a search

    @Test
    fun `nothing is searched for an empty query or one of whitespace`() {
        assertNull(TextSearch.needle(""))
        assertNull(TextSearch.needle("   \n "))
        assertNull(TextSearch.needle("́"))
    }

    @Test
    fun `a blank page has nothing to find`() {
        assertTrue(FoldedText.of("").isEmpty)
        assertTrue(FoldedText.of(" \n ").isEmpty)
        assertTrue(TextSearch.find(FoldedText.Empty, "a").isEmpty())
    }

    @Test
    fun `a query is trimmed`() {
        assertEquals(listOf("beta"), found("alpha beta", "  beta \n"))
    }

    // ------------------------------------------------------------------ against a real page

    @Test
    fun `a match is highlighted by the page it was found in`() {
        val page =
            PageText(
                lines =
                    listOf(
                        TextLine(listOf(word("The", 0.0f), word("quick", 0.1f))),
                        TextLine(
                            listOf(word("brown", 0.0f, y = 0.2f), word("fox", 0.1f, y = 0.2f))
                        ),
                    ),
                origin = ContentOrigin.EMBEDDED,
            )
        val selection = TextSelection(page)

        val span =
            TextSearch.find(FoldedText.of(selection.text), TextSearch.needle("QUICK brown")!!)
                .single()

        assertEquals("quick\nbrown", selection.text(span))
        // One box for each line the match crosses.
        assertEquals(2, selection.highlightRects(span).size)
    }

    private fun word(text: String, x: Float, y: Float = 0.1f) =
        TextWord(text, NormalizedRect(x, y, x + 0.08f, y + 0.04f))
}
