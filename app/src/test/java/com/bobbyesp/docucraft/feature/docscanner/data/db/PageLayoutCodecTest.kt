/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db

import com.bobbyesp.documentcontent.NormalizedRect
import com.bobbyesp.documentcontent.TextLine
import com.bobbyesp.documentcontent.TextWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What is written of a recognized page has to come back as it was, or not at all. */
class PageLayoutCodecTest {

    private val lines =
        listOf(
            TextLine(
                listOf(
                    TextWord("Canción", NormalizedRect(0.1f, 0.2f, 0.3f, 0.25f)),
                    TextWord("año", NormalizedRect(0.32f, 0.2f, 0.4f, 0.25f)),
                )
            ),
            // A blank line separates paragraphs, and is kept.
            TextLine(emptyList()),
            TextLine(listOf(TextWord("日本語", NormalizedRect(0f, 0.9f, 1f, 1f)))),
        )

    @Test
    fun `lines, words and boxes come back as they were written`() {
        val data = PageLayoutCodec.encode(lines)

        assertEquals(lines, PageLayoutCodec.decode(PageLayoutCodec.VERSION, data))
    }

    @Test
    fun `a page with no lines is a page with no lines`() {
        val data = PageLayoutCodec.encode(emptyList())

        assertEquals(emptyList<TextLine>(), PageLayoutCodec.decode(PageLayoutCodec.VERSION, data))
    }

    /** Written by a version of the app this one does not know: the page is recognized again. */
    @Test
    fun `a format that is not this one is read as nothing`() {
        val data = PageLayoutCodec.encode(lines)

        assertNull(PageLayoutCodec.decode(PageLayoutCodec.VERSION + 1, data))
    }

    @Test
    fun `data that is cut short is read as nothing rather than as half a page`() {
        val data = PageLayoutCodec.encode(lines)

        assertNull(PageLayoutCodec.decode(PageLayoutCodec.VERSION, data.copyOf(data.size / 2)))
        assertNull(PageLayoutCodec.decode(PageLayoutCodec.VERSION, ByteArray(0)))
    }
}
