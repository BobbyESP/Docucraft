/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.domain.details

import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentOrigin
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Folder
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Tag
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.testDocument
import com.bobbyesp.docucraft.feature.docscanner.testLinkedDocument
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A catalogued document's details come from the catalogue, and only what the catalogue does not
 * keep is read from its file; an external one's are all read from its file.
 */
class ViewerDocumentDetailsTest {

    private val catalogue = MutableStateFlow<Document?>(scanned())
    private val observeDocument: ObserveDocumentUseCase = mockk {
        every { this@mockk.invoke(UUID) } returns catalogue
    }
    private val folder = MutableStateFlow<Folder?>(null)
    private val tags = MutableStateFlow<List<Tag>>(emptyList())
    private val readLocations = mutableListOf<ContentRef>()
    private val versionLocations = mutableListOf<ContentRef>()
    private val facts =
        object : DocumentFactsReader {
            override suspend fun read(document: ContentRef): DocumentFacts {
                readLocations += document
                return DocumentFacts(sizeBytes = 2048L, pageCount = 7, pdfVersion = "1.4")
            }

            override suspend fun pdfVersion(document: ContentRef): String {
                versionLocations += document
                return "1.7"
            }
        }
    private val detected = mutableListOf<DocumentSource>()
    private val observeDetails =
        ObserveViewerDocumentDetailsUseCase(
            observeDocument = observeDocument,
            folderOf = { folder },
            tagsOf = { tags },
            facts = facts,
        ) { document ->
            detected += document
            DocumentText.Embedded
        }

    @Test
    fun `a catalogued document's details come from the catalogue`() = runTest {
        val details = observeDetails(ViewerDocumentRef.Catalogued(UUID)).first()

        assertEquals("Invoice", details?.name)
        assertEquals("March", details?.description)
        assertEquals(3, details?.pageCount)
        assertEquals(1024L, details?.sizeBytes)
        assertEquals(
            "its size and pages are not read from the file again",
            0,
            readLocations.size,
        )
    }

    @Test
    fun `what the catalogue does not keep is read from the file`() = runTest {
        val details = observeDetails(ViewerDocumentRef.Catalogued(UUID)).first()

        assertEquals("1.7", details?.pdfVersion)
        assertEquals(listOf(scanned().location), versionLocations)
    }

    @Test
    fun `an untitled document is named by its file`() = runTest {
        catalogue.value = scanned().copy(title = null)

        val details = observeDetails(ViewerDocumentRef.Catalogued(UUID)).first()

        assertEquals("Scan_20260924_101500", details?.name)
        assertNull("its file name would only repeat its name", details?.fileName)
    }

    @Test
    fun `a titled document also says what its file is called`() = runTest {
        assertEquals(
            "Scan_20260924_101500.pdf",
            observeDetails(ViewerDocumentRef.Catalogued(UUID)).first()?.fileName,
        )
    }

    @Test
    fun `a document the app keeps says how it got here and where it is`() = runTest {
        val invoices = folder("Invoices")
        val paid = Tag("tag-1", "Paid", color = null, homePosition = null)
        folder.value = invoices
        tags.value = listOf(paid)
        catalogue.value = scanned().copy(isFavorite = true, contentUpdatedAtEpochMillis = 9_000L)

        val library = observeDetails(ViewerDocumentRef.Catalogued(UUID)).first()?.library

        assertEquals(DocumentOrigin.SCAN, library?.origin)
        assertEquals(5_000L, library?.enteredAtEpochMillis)
        assertEquals(9_000L, library?.modifiedAtEpochMillis)
        assertEquals(invoices, library?.folder)
        assertEquals(listOf(paid), library?.tags)
        assertEquals(true, library?.isFavorite)
    }

    @Test
    fun `a document that has not changed since it entered has no modification to tell`() = runTest {
        assertNull(
            observeDetails(ViewerDocumentRef.Catalogued(UUID))
                .first()
                ?.library
                ?.modifiedAtEpochMillis
        )
    }

    @Test
    fun `a document of another app has nothing of the library`() = runTest {
        catalogue.value = testLinkedDocument(uuid = UUID)

        assertNull(observeDetails(ViewerDocumentRef.Catalogued(UUID)).first()?.library)
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
            ViewerDocumentDetails(
                "a.pdf",
                null,
                pageCount = 7,
                sizeBytes = 2048L,
                pdfVersion = "1.4",
            ),
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

    /** Renaming or tagging a document changes its details, not its file. */
    @Test
    fun `the file is not read again when the document is renamed`() = runTest {
        val emitted = mutableListOf<ViewerDocumentDetails?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observeDetails(ViewerDocumentRef.Catalogued(UUID)).toList(emitted)
        }

        catalogue.value = scanned().copy(title = "Invoice, March")

        assertEquals("Invoice, March", emitted.last()?.name)
        assertEquals(DocumentText.Embedded, emitted.last()?.text)
        assertEquals(1, detected.size)
        assertEquals(1, versionLocations.size)
    }

    @Test
    fun `the version is the one in the header, wherever it starts`() {
        assertEquals("1.7", pdfVersionIn("%PDF-1.7\n%âãÏÓ"))
        assertEquals("2.0", pdfVersionIn("ï»¿%PDF-2.0\n"))
        assertNull(pdfVersionIn("PK\u0003\u0004 not a pdf"))
    }

    private fun scanned() =
        testDocument(
            uuid = UUID,
            originalName = "Scan_20260924_101500",
            title = "Invoice",
            description = "March",
            location =
                ContentRef("content://com.bobbyesp.docucraft.fileprovider/documents/doc-1.pdf"),
            createdAtEpochMillis = 5_000L,
            sizeBytes = 1024L,
            pageCount = 3,
        )

    private fun folder(name: String) =
        Folder(
            uuid = "folder-1",
            name = name,
            parentUuid = null,
            color = null,
            icon = null,
            pinnedAtEpochMillis = null,
            sort = null,
            createdAtEpochMillis = 0L,
        )

    private companion object {
        const val UUID = "doc-1"
        const val EXTERNAL = "content://media/external/downloads/37"
    }
}
