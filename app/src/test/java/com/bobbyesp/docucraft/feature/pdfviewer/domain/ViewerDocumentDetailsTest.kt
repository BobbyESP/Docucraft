/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.domain

import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.domain.details.DocumentFacts
import com.bobbyesp.docucraft.feature.pdfviewer.domain.details.DocumentFactsReader
import com.bobbyesp.docucraft.feature.pdfviewer.domain.details.ObserveViewerDocumentDetailsUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.domain.details.ViewerDocumentDetails
import com.bobbyesp.docucraft.feature.pdfviewer.domain.model.ViewerDocumentRef
import com.bobbyesp.scanner.ContentRef
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * V5 in the phase 3 analysis: the details used to be rebuilt from what the viewer had left — a size
 * recomputed from a composable, a page count borrowed from the rendering engine. A catalogued
 * document's now come from the catalogue; only an external one is read from its file.
 */
class ViewerDocumentDetailsTest {

    private val catalogue = MutableStateFlow<ScannedDocument?>(scanned())
    private val observeDocument: ObserveDocumentUseCase = mockk {
        every { this@mockk.invoke(UUID) } returns catalogue
    }
    private val readLocations = mutableListOf<ContentRef>()
    private val facts = DocumentFactsReader { location ->
        readLocations += location
        DocumentFacts(sizeBytes = 2048L, pageCount = 7)
    }
    private val observeDetails = ObserveViewerDocumentDetailsUseCase(observeDocument, facts)

    @Test
    fun aCataloguedDocumentsDetailsComeFromTheCatalogue() = runTest {
        val details = observeDetails(ViewerDocumentRef.Catalogued(UUID)).first()

        assertEquals(
            ViewerDocumentDetails("Invoice", "March", pageCount = 3, sizeBytes = 1024L),
            details,
        )
        assertEquals("the file is not read for a catalogued document", 0, readLocations.size)
    }

    @Test
    fun anUntitledDocumentIsNamedByItsFile() = runTest {
        catalogue.value = scanned().copy(title = null)

        assertEquals(
            "Scan_20260924_101500",
            observeDetails(ViewerDocumentRef.Catalogued(UUID)).first()?.name,
        )
    }

    @Test
    fun aDeletedDocumentHasNoDetails() = runTest {
        catalogue.value = null

        assertNull(observeDetails(ViewerDocumentRef.Catalogued(UUID)).first())
    }

    @Test
    fun anExternalDocumentsDetailsAreReadFromItsFile() = runTest {
        val ref = ViewerDocumentRef.External(uri = EXTERNAL, displayName = "a.pdf")

        val details = observeDetails(ref).first()

        assertEquals(
            ViewerDocumentDetails("a.pdf", null, pageCount = 7, sizeBytes = 2048L),
            details,
        )
        assertEquals(listOf(ContentRef(EXTERNAL)), readLocations)
    }

    private fun scanned() =
        ScannedDocument(
            uuid = UUID,
            filename = "Scan_20260924_101500",
            title = "Invoice",
            description = "March",
            location =
                ContentRef("content://com.bobbyesp.docucraft.fileprovider/documents/doc-1.pdf"),
            capturedAtEpochMillis = 0L,
            sizeBytes = 1024L,
            pageCount = 3,
            thumbnail = null,
        )

    private companion object {
        const val UUID = "doc-1"
        const val EXTERNAL = "content://media/external/downloads/37"
    }
}
