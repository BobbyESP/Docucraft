/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.domain.details

import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.testDocument
import com.bobbyesp.docucraft.feature.pdfviewer.domain.model.ViewerDocumentRef
import com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase.DocumentText
import com.bobbyesp.documentcontent.DocumentSource
import com.bobbyesp.scanner.ContentRef
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A catalogued document's details come from the catalogue; only an external one's are read from its
 * file.
 */
class ViewerDocumentDetailsTest {

    private val catalogue = MutableStateFlow<Document?>(scanned())
    private val observeDocument: ObserveDocumentUseCase = mockk {
        every { this@mockk.invoke(UUID) } returns catalogue
    }
    private val readLocations = mutableListOf<ContentRef>()
    private val facts = DocumentFactsReader { location ->
        readLocations += location
        DocumentFacts(sizeBytes = 2048L, pageCount = 7)
    }
    private val detected = mutableListOf<DocumentSource>()
    private val observeDetails =
        ObserveViewerDocumentDetailsUseCase(observeDocument, facts) { document ->
            detected += document
            DocumentText.Embedded
        }

    @Test
    fun `a catalogued document's details come from the catalogue`() = runTest {
        val details = observeDetails(ViewerDocumentRef.Catalogued(UUID)).first()

        assertEquals(
            ViewerDocumentDetails("Invoice", "March", pageCount = 3, sizeBytes = 1024L),
            details,
        )
        assertEquals("the file is not read for a catalogued document", 0, readLocations.size)
    }

    @Test
    fun `an untitled document is named by its file`() = runTest {
        catalogue.value = scanned().copy(title = null)

        assertEquals(
            "Scan_20260924_101500",
            observeDetails(ViewerDocumentRef.Catalogued(UUID)).first()?.name,
        )
    }

    @Test
    fun `a deleted document has no details`() = runTest {
        catalogue.value = null

        assertNull(observeDetails(ViewerDocumentRef.Catalogued(UUID)).first())
    }

    @Test
    fun `an external document's details are read from its file`() = runTest {
        val ref = ViewerDocumentRef.External(uri = EXTERNAL, displayName = "a.pdf")

        val details = observeDetails(ref).first()

        assertEquals(
            ViewerDocumentDetails("a.pdf", null, pageCount = 7, sizeBytes = 2048L),
            details,
        )
        assertEquals(listOf(ContentRef(EXTERNAL)), readLocations)
    }

    /** Looking for text reads pages, so the details come out first without it, then with it. */
    @Test
    fun `whether the document has text follows the rest of the details`() = runTest {
        val emitted = observeDetails(ViewerDocumentRef.Catalogued(UUID)).take(2).toList()

        assertEquals(listOf(null, DocumentText.Embedded), emitted.map { it?.text })
        assertEquals(listOf(DocumentSource(scanned().location.value)), detected)
    }

    private fun scanned() =
        testDocument(
            uuid = UUID,
            filename = "Scan_20260924_101500",
            title = "Invoice",
            description = "March",
            location =
                ContentRef("content://com.bobbyesp.docucraft.fileprovider/documents/doc-1.pdf"),
            capturedAtEpochMillis = 0L,
            sizeBytes = 1024L,
            pageCount = 3,
        )

    private companion object {
        const val UUID = "doc-1"
        const val EXTERNAL = "content://media/external/downloads/37"
    }
}
