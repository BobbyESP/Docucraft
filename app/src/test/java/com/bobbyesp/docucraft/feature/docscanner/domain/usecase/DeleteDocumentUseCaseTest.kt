/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.FakeDocumentStorage
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LocalDocumentsRepository
import com.bobbyesp.scanner.ContentRef
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deleting used to remove the row and quietly leave both files behind, so this covers what is
 * actually meant to disappear.
 */
class DeleteDocumentUseCaseTest {

    private val storage = FakeDocumentStorage()
    private val repository = mockk<LocalDocumentsRepository>(relaxed = true)
    private val useCase = DeleteDocumentUseCase(repository, storage)

    private fun document(thumbnail: ContentRef? = ContentRef("/previews/doc.png")) =
        ScannedDocument(
            uuid = "doc-1",
            filename = "doc",
            title = null,
            description = null,
            location = ContentRef("content://stored/doc.pdf"),
            capturedAtEpochMillis = 1_000L,
            sizeBytes = 2_048L,
            pageCount = 1,
            thumbnail = thumbnail,
        )

    @Test
    fun `removes the document from the catalogue and from storage`() = runTest {
        val document = document()

        useCase(document)

        coVerify { repository.deleteDocument(document.location) }
        assertTrue(ContentRef("content://stored/doc.pdf") in storage.deleted)
    }

    /** Previews of deleted documents used to pile up forever. */
    @Test
    fun `takes the preview with it`() = runTest {
        useCase(document(thumbnail = ContentRef("/previews/doc.png")))

        assertEquals(
            listOf(ContentRef("content://stored/doc.pdf"), ContentRef("/previews/doc.png")),
            storage.deleted,
        )
    }

    @Test
    fun `a document with no preview deletes just the document`() = runTest {
        useCase(document(thumbnail = null))

        assertEquals(listOf(ContentRef("content://stored/doc.pdf")), storage.deleted)
    }
}
