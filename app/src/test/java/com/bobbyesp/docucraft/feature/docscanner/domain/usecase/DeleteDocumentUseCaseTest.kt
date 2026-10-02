/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.FakeDocumentStorage
import com.bobbyesp.docucraft.feature.docscanner.FakeDocumentThumbnails
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.testDocument
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Deleting a document takes its catalogue row, its file and its previews. */
class DeleteDocumentUseCaseTest {

    private val storage = FakeDocumentStorage()
    private val thumbnails = FakeDocumentThumbnails()
    private val repository = mockk<DocumentsRepository>(relaxed = true)
    private val useCase = DeleteDocumentUseCase(repository, storage, thumbnails)

    private val document = testDocument(uuid = "doc-1", filePath = "documents/doc-1.pdf")

    @Test
    fun `removes the document from the catalogue and from storage`() = runTest {
        useCase(document)

        coVerify { repository.deleteDocument("doc-1") }
        assertEquals(listOf("documents/doc-1.pdf"), storage.deleted)
    }

    // A preview is drawn from the document, so one left behind is of a document nobody has.
    @Test
    fun `its previews are forgotten`() = runTest {
        useCase(document)

        assertEquals(listOf("doc-1"), thumbnails.discarded)
    }
}
