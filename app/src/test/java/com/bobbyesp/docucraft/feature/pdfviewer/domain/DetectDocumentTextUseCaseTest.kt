/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.domain

import com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase.DetectDocumentTextUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase.DocumentText
import com.bobbyesp.documentcontent.ContentOrigin
import com.bobbyesp.documentcontent.DocumentSource
import com.bobbyesp.documentcontent.NormalizedRect
import com.bobbyesp.documentcontent.PageContentProvider
import com.bobbyesp.documentcontent.PageContentResult
import com.bobbyesp.documentcontent.PageContentSession
import com.bobbyesp.documentcontent.PageText
import com.bobbyesp.documentcontent.TextLine
import com.bobbyesp.documentcontent.TextWord
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectDocumentTextUseCaseTest {

    @Test
    fun `a typed page among the first ones makes the text selectable`() = runTest {
        val (detect, provider) =
            detector(
                PageContentResult.NoText,
                PageContentResult.NoText,
                text(ContentOrigin.EMBEDDED),
            )

        assertEquals(DocumentText.Embedded, detect(DOCUMENT))
        assertTrue("the session is closed", provider.closed)
    }

    @Test
    fun `recognized text is told apart`() = runTest {
        assertEquals(
            DocumentText.Recognized,
            detector(text(ContentOrigin.RECOGNIZED)).first(DOCUMENT),
        )
    }

    @Test
    fun `a document of images has none, however short`() = runTest {
        assertEquals(
            DocumentText.None,
            detector(PageContentResult.NoText, PageContentResult.NoText).first(DOCUMENT),
        )
    }

    @Test
    fun `a device that cannot read text says so`() = runTest {
        assertEquals(
            DocumentText.Unsupported,
            detector(PageContentResult.Unsupported).first(DOCUMENT),
        )
    }

    @Test
    fun `a document that cannot be read is unknown`() = runTest {
        assertEquals(DocumentText.Unknown, detector().first(DOCUMENT))
    }

    @Test
    fun `only the first few pages are looked at`() = runTest {
        val pages = List(5) { PageContentResult.NoText } + text(ContentOrigin.EMBEDDED)
        val (detect, provider) = detector(*pages.toTypedArray())

        assertEquals(DocumentText.None, detect(DOCUMENT))
        assertEquals(5, provider.reads)
    }

    private fun detector(
        vararg pages: PageContentResult
    ): Pair<DetectDocumentTextUseCase, FakeProvider> {
        val provider = FakeProvider(pages.toList())
        return DetectDocumentTextUseCase(provider) to provider
    }

    private fun text(origin: ContentOrigin) =
        PageContentResult.Available(
            text =
                PageText(
                    lines =
                        listOf(
                            TextLine(listOf(TextWord("word", NormalizedRect(0f, 0f, 0.1f, 0.1f))))
                        ),
                    origin = origin,
                ),
            links = emptyList(),
        )

    private class FakeProvider(private val pages: List<PageContentResult>) : PageContentProvider {
        var reads = 0
        var closed = false

        override val origin = ContentOrigin.EMBEDDED

        override suspend fun open(document: DocumentSource): PageContentSession =
            object : PageContentSession {
                override suspend fun page(index: Int): PageContentResult {
                    reads++
                    return pages.getOrNull(index)
                        ?: PageContentResult.Failed(IndexOutOfBoundsException("$index"))
                }

                override fun close() {
                    closed = true
                }
            }
    }

    private companion object {
        val DOCUMENT = DocumentSource("content://test/doc.pdf")
    }
}
