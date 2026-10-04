/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.FakeDocumentActivityRepository
import com.bobbyesp.docucraft.feature.docscanner.FakeDocumentStorage
import com.bobbyesp.docucraft.feature.docscanner.FakeDocumentThumbnails
import com.bobbyesp.docucraft.feature.docscanner.FakeDocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.indexing.DocumentIndexQueue
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentAvailability
import com.bobbyesp.docucraft.feature.docscanner.testDocument
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bin and the reconciliation, over a catalogue and a storage held in memory. What they must
 * never do is as much the subject as what they do: no document of the library is deleted by any of
 * them.
 */
class BinUseCasesTest {

    private val day = 24L * 60 * 60 * 1000

    private val storage = FakeDocumentStorage()
    private val thumbnails = FakeDocumentThumbnails()
    private val activity = FakeDocumentActivityRepository()
    private val queued = mutableListOf<String>()
    private val queue =
        object : DocumentIndexQueue {
            override fun enqueue(documentUuid: String) {
                queued += documentUuid
            }
        }

    private fun document(uuid: String, binnedAt: Long? = null): Document.Managed =
        testDocument(uuid = uuid).copy(trashedAtEpochMillis = binnedAt)

    private fun catalogue(vararg documents: Document.Managed): FakeDocumentsRepository {
        storage.files += documents.map { it.filePath }
        return FakeDocumentsRepository(documents.toList())
    }

    private fun deleteFromBin(documents: FakeDocumentsRepository) =
        DeleteFromBinUseCase(documents, storage, thumbnails)

    // ---------------- the bin ----------------

    @Test
    fun `a deleted document leaves the library and keeps its file`() = runTest {
        val documents = catalogue(document("a"))

        assertTrue(MoveDocumentToBinUseCase(documents)("a"))

        assertTrue(documents.observeDocuments().first().isEmpty())
        assertEquals(listOf("a"), documents.observeBin().first().map { it.uuid })
        assertEquals(listOf("documents/a.pdf"), storage.files)
        assertTrue(storage.deleted.isEmpty())
    }

    @Test
    fun `a restored document is back in the library and has its text read again`() = runTest {
        val documents = catalogue(document("a", binnedAt = 5))

        assertTrue(RestoreDocumentUseCase(documents, queue)("a"))

        assertEquals(listOf("a"), documents.observeDocuments().first().map { it.uuid })
        assertEquals(listOf("a"), queued)
    }

    @Test
    fun `restoring what is not in the bin does nothing`() = runTest {
        val documents = catalogue(document("a"))

        assertFalse(RestoreDocumentUseCase(documents, queue)("a"))
        assertTrue(queued.isEmpty())
    }

    @Test
    fun `deleting for good takes the row, the file and the previews`() = runTest {
        val binned = document("a", binnedAt = 5)
        val documents = catalogue(binned)

        assertTrue(deleteFromBin(documents)(binned))

        assertEquals(listOf("a"), documents.deleted)
        assertEquals(listOf("documents/a.pdf"), storage.deleted)
        assertEquals(listOf("a"), thumbnails.discarded)
    }

    // The rule everything here answers to: only the bin's documents are ever deleted.
    @Test
    fun `a document of the library is never deleted for good`() = runTest {
        val kept = document("a")
        val documents = catalogue(kept)

        assertFalse(deleteFromBin(documents)(kept))

        assertTrue(documents.deleted.isEmpty())
        assertTrue(storage.deleted.isEmpty())
    }

    // Two documents of an older catalogue can share a file. It goes with the last of them.
    @Test
    fun `a file another document still has is left`() = runTest {
        val binned = document("a", binnedAt = 5).copy(filePath = "scans/pdf/shared.pdf")
        val other = document("b").copy(filePath = "scans/pdf/shared.pdf")
        val documents = FakeDocumentsRepository(listOf(binned, other))
        storage.files += "scans/pdf/shared.pdf"

        assertTrue(deleteFromBin(documents)(binned))

        assertTrue(storage.deleted.isEmpty())
    }

    @Test
    fun `a file that cannot be removed does not bring the document back`() = runTest {
        val binned = document("a", binnedAt = 5)
        val documents = catalogue(binned)
        storage.deleteFailure = IllegalStateException("disk")

        assertTrue(deleteFromBin(documents)(binned))

        assertEquals(listOf("a"), documents.deleted)
    }

    @Test
    fun `emptying the bin deletes what is in it and nothing else`() = runTest {
        val documents = catalogue(document("kept"), document("a", binnedAt = 5), document("b", 6))

        assertEquals(2, EmptyBinUseCase(documents, deleteFromBin(documents))())

        assertEquals(listOf("kept"), documents.documents.value.map { it.uuid })
    }

    @Test
    fun `the bin lets go of what has been there for thirty days`() = runTest {
        val now = 100 * day
        val documents =
            catalogue(
                document("kept"),
                document("old", binnedAt = now - 30 * day),
                document("recent", binnedAt = now - 30 * day + 1),
            )

        val purged = PurgeExpiredBinUseCase(documents, deleteFromBin(documents), now = { now })()

        assertEquals(1, purged)
        assertEquals(listOf("kept", "recent"), documents.documents.value.map { it.uuid })
    }

    @Test
    fun `the days a document has left count the one that has started`() {
        assertEquals(30, BinRetention.daysLeft(binnedAtEpochMillis = 0, nowEpochMillis = 0))
        assertEquals(30, BinRetention.daysLeft(0, nowEpochMillis = day - 1))
        assertEquals(29, BinRetention.daysLeft(0, nowEpochMillis = day))
        assertEquals(1, BinRetention.daysLeft(0, nowEpochMillis = 30 * day - 1))
        assertEquals(0, BinRetention.daysLeft(0, nowEpochMillis = 30 * day))
        assertEquals(0, BinRetention.daysLeft(0, nowEpochMillis = 40 * day))
    }

    // ---------------- reconciliation ----------------

    private fun reconcile(documents: FakeDocumentsRepository, now: Long) =
        ReconcileStorageUseCase(documents, activity, storage, thumbnails, now = { now })

    @Test
    fun `a file nothing refers to is removed once it is a day old`() = runTest {
        val now = 10 * day
        val documents = catalogue(document("a"))
        storage.files += listOf("documents/orphan.pdf", "documents/saving.pdf.tmp")
        storage.lastModified["documents/a.pdf"] = 0
        storage.lastModified["documents/orphan.pdf"] = now - day
        // Being written right now: the document it belongs to is not catalogued yet.
        storage.lastModified["documents/saving.pdf.tmp"] = now - 1

        val result = reconcile(documents, now)()

        assertEquals(listOf("documents/orphan.pdf"), result.deletedFiles)
        assertEquals(listOf("documents/a.pdf", "documents/saving.pdf.tmp"), storage.files)
    }

    @Test
    fun `a document without its file is marked as not found and never deleted`() = runTest {
        val documents = catalogue(document("here"), document("gone"), document("binned", 5))
        storage.files -= "documents/gone.pdf"

        val result = reconcile(documents, now = day)()

        assertEquals(listOf("gone"), result.notFound)
        assertEquals(listOf("gone" to DocumentAvailability.NOT_FOUND), activity.availability)
        assertEquals(3, documents.documents.value.size)
        assertTrue(documents.deleted.isEmpty())
    }

    @Test
    fun `a document whose file is back stops being not found`() = runTest {
        val documents = catalogue(document("a"))
        activity.notFound.value = setOf("a")

        val result = reconcile(documents, now = day)()

        assertEquals(listOf("a"), result.foundAgain)
        assertTrue(activity.notFound.value.isEmpty())
    }

    @Test
    fun `what is already known is not written again`() = runTest {
        val documents = catalogue(document("here"), document("gone"))
        storage.files -= "documents/gone.pdf"
        activity.notFound.value = setOf("gone")

        val result = reconcile(documents, now = day)()

        assertEquals(Reconciliation(), result)
        assertTrue(activity.availability.isEmpty())
    }

    @Test
    fun `the previews of documents that are gone are forgotten`() = runTest {
        val documents = catalogue(document("a"), document("b", binnedAt = 5))

        reconcile(documents, now = day)()

        assertEquals(setOf("a", "b"), thumbnails.retained)
    }
}
