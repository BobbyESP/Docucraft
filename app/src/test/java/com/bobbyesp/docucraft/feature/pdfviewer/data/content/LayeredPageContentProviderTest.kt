/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.data.content

import com.bobbyesp.docucraft.feature.pdfviewer.FakePageContentProvider
import com.bobbyesp.docucraft.feature.pdfviewer.textPage
import com.bobbyesp.documentcontent.ContentOrigin
import com.bobbyesp.documentcontent.DocumentSource
import com.bobbyesp.documentcontent.NormalizedRect
import com.bobbyesp.documentcontent.PageContentResult
import com.bobbyesp.documentcontent.PageLink
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LayeredPageContentProviderTest {

    private val document = DocumentSource("content://test/doc.pdf")
    private val link = PageLink.External(listOf(NormalizedRect(0f, 0f, 0.1f, 0.1f)), "https://a.b")
    private val typed = textPage("typed")
    private val read = textPage("read", origin = ContentOrigin.RECOGNIZED)

    @Test
    fun `without recognition the document's own session is used as it is`() = runTest {
        val session =
            LayeredPageContentProvider(embedded(0 to PageContentResult.NoText), recognized = null)
                .open(document)

        assertEquals(PageContentResult.NoText, session.page(0))
    }

    @Test
    fun `a page with its own text never reaches recognition`() = runTest {
        val recognizer = recognized(0 to read)
        val session = LayeredPageContentProvider(embedded(0 to typed), recognizer).open(document)

        assertSame(typed, session.page(0))
        assertEquals("recognition is only opened when a page needs it", 0, recognizer.opened)
    }

    @Test
    fun `a scanned page is read by recognition, page by page`() = runTest {
        val recognizer = recognized(1 to read)
        val session =
            LayeredPageContentProvider(
                    embedded(0 to typed, 1 to PageContentResult.NoText),
                    recognizer,
                )
                .open(document)

        assertSame(typed, session.page(0))
        assertSame(read, session.page(1))
        session.page(1)
        assertEquals("opened once for the whole session", 1, recognizer.opened)
    }

    @Test
    fun `where the platform cannot look, recognition can`() = runTest {
        val session =
            LayeredPageContentProvider(
                    embedded(0 to PageContentResult.Unsupported),
                    recognized(0 to read),
                )
                .open(document)

        assertSame(read, session.page(0))
    }

    @Test
    fun `recognized text keeps the document's links`() = runTest {
        val linksOnly = PageContentResult.Available(text = null, links = listOf(link))
        val session =
            LayeredPageContentProvider(embedded(0 to linksOnly), recognized(0 to read))
                .open(document)

        val page = session.page(0) as PageContentResult.Available
        assertEquals(read.text, page.text)
        assertEquals(listOf(link), page.links)
    }

    @Test
    fun `recognition finding nothing is truer than the platform not looking`() = runTest {
        val session =
            LayeredPageContentProvider(
                    embedded(0 to PageContentResult.Unsupported),
                    recognized(0 to PageContentResult.NoText),
                )
                .open(document)

        assertEquals(PageContentResult.NoText, session.page(0))
    }

    @Test
    fun `a failed recognition leaves the document's own answer`() = runTest {
        val session =
            LayeredPageContentProvider(
                    embedded(0 to PageContentResult.NoText),
                    recognized(0 to PageContentResult.Failed(IllegalStateException())),
                )
                .open(document)

        assertEquals(PageContentResult.NoText, session.page(0))
    }

    @Test
    fun `closing closes both sessions`() = runTest {
        val embedded = embedded(0 to PageContentResult.NoText)
        val recognizer = recognized(0 to read)
        val session = LayeredPageContentProvider(embedded, recognizer).open(document)
        session.page(0)

        session.close()

        assertTrue(embedded.sessions.single().closed)
        assertTrue(recognizer.sessions.single().closed)
    }

    private fun embedded(vararg pages: Pair<Int, PageContentResult>) =
        FakePageContentProvider(pages.toMap())

    private fun recognized(vararg pages: Pair<Int, PageContentResult>) =
        FakePageContentProvider(pages.toMap(), ContentOrigin.RECOGNIZED)
}
