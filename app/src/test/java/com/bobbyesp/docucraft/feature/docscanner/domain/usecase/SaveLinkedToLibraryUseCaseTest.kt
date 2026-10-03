/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.FakeDocumentIndexQueue
import com.bobbyesp.docucraft.feature.docscanner.FakeDocumentStorage
import com.bobbyesp.docucraft.feature.docscanner.FakeDocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.FakeExternalDocumentAccess
import com.bobbyesp.docucraft.feature.docscanner.FakeLinkedDocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.exception.ScanSaveException
import com.bobbyesp.docucraft.feature.docscanner.testDocument
import com.bobbyesp.docucraft.feature.docscanner.testLinkedDocument
import com.bobbyesp.scanner.ContentRef
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Saving another app's document into the library. Whatever happens, the library is never left with
 * half of it: a file nothing refers to, or a document whose file is not there.
 */
class SaveLinkedToLibraryUseCaseTest {

    private val shared = testLinkedDocument(uuid = LINKED, location = LOCATION)
    private val documents =
        FakeDocumentsRepository(
            documents = listOf(testDocument(uuid = "doc-1")),
            linked = listOf(shared),
        )
    private val linked = FakeLinkedDocumentsRepository()
    private val storage = FakeDocumentStorage()
    private val access = FakeExternalDocumentAccess()
    private val indexQueue = FakeDocumentIndexQueue()
    private val save = SaveLinkedToLibraryUseCase(documents, linked, storage, access, indexQueue)

    @Test
    fun `its file is copied under its own uuid, and the reference becomes a document`() = runTest {
        val outcome = save(LINKED)

        assertEquals(SaveToLibraryOutcome.Saved, outcome)
        assertEquals(listOf("documents/$LINKED.pdf"), storage.files)
        val (uuid, stored) = linked.kept.single()
        assertEquals(LINKED, uuid)
        assertEquals("documents/$LINKED.pdf", stored.filePath)
        assertEquals(3, stored.pageCount)
    }

    /** It is in the library now, where documents are found by what they say. */
    @Test
    fun `once kept it is queued to have its text read, and not before`() = runTest {
        storage.pageCount = null
        save(LINKED)
        assertTrue(indexQueue.queued.isEmpty())

        storage.pageCount = 3
        save(LINKED)
        assertEquals(listOf(LINKED), indexQueue.queued)
    }

    /** The app reads its own copy from then on. */
    @Test
    fun `what was held to read the other app's file is given back`() = runTest {
        save(LINKED)

        assertEquals(listOf("release" to LOCATION), access.calls)
    }

    // --- already there ---

    @Test
    fun `a document the library already has is not saved again without asking`() = runTest {
        documents.hashes["doc-1"] = "hash-of-$LINKED"

        val outcome = save(LINKED)

        assertEquals(SaveToLibraryOutcome.AlreadyInLibrary(existingUuid = "doc-1"), outcome)
        assertTrue(linked.kept.isEmpty())
        // The copy made to compare it is not left behind.
        assertTrue(storage.files.isEmpty())
        // It is still another app's: what is held for it stays held.
        assertTrue(access.calls.isEmpty())
    }

    @Test
    fun `it is saved again when that is what was asked`() = runTest {
        documents.hashes["doc-1"] = "hash-of-$LINKED"

        val outcome = save(LINKED, evenIfAlreadyThere = true)

        assertEquals(SaveToLibraryOutcome.Saved, outcome)
        assertEquals(listOf("documents/$LINKED.pdf"), storage.files)
        assertEquals(LINKED, linked.kept.single().first)
    }

    // --- what cannot be kept ---

    /** Protected or damaged: the library only keeps what it can show. */
    @Test
    fun `a file that cannot be opened is not kept`() = runTest {
        storage.pageCount = null

        val outcome = save(LINKED)

        assertEquals(SaveToLibraryOutcome.NotReadable, outcome)
        assertTrue(linked.kept.isEmpty())
        assertTrue(storage.files.isEmpty())
    }

    /** The other app's loan has lapsed, or its file is gone. */
    @Test
    fun `a file that cannot be copied is an answer, not a crash`() = runTest {
        storage.storeFailure = ScanSaveException.OutputFileNotCopied()

        assertEquals(SaveToLibraryOutcome.NotReadable, save(LINKED))
        assertTrue(linked.kept.isEmpty())
    }

    @Test
    fun `an empty file is not kept`() = runTest {
        storage.sizeBytes = 0

        assertEquals(SaveToLibraryOutcome.NotReadable, save(LINKED))
        assertTrue(storage.files.isEmpty())
    }

    // --- nothing to save ---

    @Test
    fun `a document the app already keeps is left alone`() = runTest {
        assertEquals(SaveToLibraryOutcome.NothingToSave, save("doc-1"))
        assertEquals(SaveToLibraryOutcome.NothingToSave, save("no-such-document"))

        assertTrue(storage.files.isEmpty())
        assertTrue(linked.kept.isEmpty())
    }

    /** It was saved, or removed, while its file was being copied. */
    @Test
    fun `a copy made for a document that is no longer linked is removed`() = runTest {
        linked.keeps = false

        val outcome = save(LINKED)

        assertEquals(SaveToLibraryOutcome.NothingToSave, outcome)
        assertTrue(storage.files.isEmpty())
        assertTrue(access.calls.isEmpty())
    }

    @Test
    fun `when the catalogue fails the copy is removed and the failure is not hidden`() = runTest {
        linked.keepFailure = IllegalStateException("database is locked")

        assertThrows(IllegalStateException::class.java) { runBlockingSave() }

        assertTrue(storage.files.isEmpty())
        assertEquals(listOf("documents/$LINKED.pdf"), storage.deleted)
    }

    private fun runBlockingSave() = kotlinx.coroutines.runBlocking { save(LINKED) }

    private companion object {
        const val LINKED = "linked-1"
        val LOCATION = ContentRef("content://other.app/documents/7")
    }
}
