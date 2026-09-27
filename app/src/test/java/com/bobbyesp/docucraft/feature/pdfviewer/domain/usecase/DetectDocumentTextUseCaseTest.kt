/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase

import com.bobbyesp.docucraft.feature.pdfviewer.FakePageContentProvider
import com.bobbyesp.docucraft.feature.pdfviewer.textPage
import com.bobbyesp.documentcontent.ContentOrigin
import com.bobbyesp.documentcontent.DocumentSource
import com.bobbyesp.documentcontent.PageContentResult
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
                textPage("word"),
            )

        assertEquals(DocumentText.Embedded, detect(DOCUMENT))
        assertTrue("the session is closed", provider.sessions.single().closed)
    }

    @Test
    fun `recognized text is told apart`() = runTest {
        assertEquals(
            DocumentText.Recognized,
            detector(textPage("word", origin = ContentOrigin.RECOGNIZED)).first(DOCUMENT),
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
        val pages = List(5) { PageContentResult.NoText } + textPage("word")
        val (detect, provider) = detector(*pages.toTypedArray())

        assertEquals(DocumentText.None, detect(DOCUMENT))
        assertEquals(5, provider.reads)
    }

    private fun detector(
        vararg pages: PageContentResult
    ): Pair<DetectDocumentTextUseCase, FakePageContentProvider> {
        val provider = FakePageContentProvider(pages.withIndex().associate { it.index to it.value })
        return DetectDocumentTextUseCase(provider) to provider
    }

    private companion object {
        val DOCUMENT = DocumentSource("content://test/doc.pdf")
    }
}
