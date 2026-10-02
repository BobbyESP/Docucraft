/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.FakeDocumentStorage
import com.bobbyesp.docucraft.feature.docscanner.FakeDocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.exception.ScanSaveException
import com.bobbyesp.scanner.ContentRef
import com.bobbyesp.scanner.ScanArtifact
import com.bobbyesp.scanner.ScanDraft
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Storage and the catalogue sit behind ports, so the save step runs on the JVM and every failure it
 * has to handle is a field on [FakeDocumentStorage] or [FakeDocumentsRepository].
 */
class SaveScanDraftUseCaseTest {

    private val storage = FakeDocumentStorage()
    private val repository = FakeDocumentsRepository()
    private val useCase = SaveScanDraftUseCase(storage, repository, newUuid = { "new-uuid" })

    private fun draft(pages: Int = 3, capturedAt: Long = 1_700_000_000_000L) =
        ScanDraft(
            artifacts = listOf(ScanArtifact.Pdf(ContentRef("content://scan/raw.pdf"), pages)),
            capturedAtEpochMillis = capturedAt,
        )

    @Test
    fun `catalogues the stored document, not the one the scanner handed over`() = runTest {
        val result = useCase(draft(pages = 3))

        assertEquals("new-uuid", result.getOrThrow())

        val scan = repository.added.single()
        assertEquals("new-uuid", scan.uuid)
        assertEquals("documents/new-uuid.pdf", scan.filePath)
        assertEquals(1_024L, scan.sizeBytes)
        assertEquals("hash-of-new-uuid", scan.contentHash)
        assertEquals(3, scan.pageCount)
        assertEquals(1_700_000_000_000L, scan.capturedAtEpochMillis)
    }

    // The file is named after the uuid, so the two have to be the same one. A second uuid for the
    // catalogue would leave a document pointing at another document's file name.
    @Test
    fun `the document is catalogued under the uuid its file was stored with`() = runTest {
        var given = 0
        val counting = SaveScanDraftUseCase(storage, repository, newUuid = { "uuid-${++given}" })

        counting(draft())

        assertEquals(1, given)
        assertEquals("documents/${repository.added.single().uuid}.pdf", storage.files.single())
    }

    @Test
    fun `the scan is named after when it was captured`() = runTest {
        useCase(draft())

        // The exact stamp is the device's local time, so only its shape is worth asserting.
        val name = repository.added.single().originalName
        assertTrue(name, name.startsWith("Scan_"))
        assertEquals("Scan_yyyyMMdd_HHmmss".length, name.length)
    }

    // A scanner that does not report its pages reports none. The catalogue rejects a document
    // with no pages, so the pages are those counted in the stored file.
    @Test
    fun `when the scanner reports no pages they are counted in the file`() = runTest {
        storage.pageCount = 7

        useCase(draft(pages = 0))

        assertEquals(7, repository.added.single().pageCount)
    }

    @Test
    fun `what the scanner reports wins over what is counted`() = runTest {
        storage.pageCount = 7

        useCase(draft(pages = 2))

        assertEquals(2, repository.added.single().pageCount)
    }

    @Test
    fun `a file with no pages reported that cannot be read is not catalogued and is removed`() =
        runTest {
            storage.pageCount = null

            val error = useCase(draft(pages = 0)).exceptionOrNull()

            assertTrue("was $error", error is ScanSaveException.UnreadableDocument)
            assertEquals(emptyList<Any>(), repository.added)
            assertEquals(emptyList<String>(), storage.files)
        }

    @Test
    fun `a draft carrying no pdf is not catalogued`() = runTest {
        val empty = ScanDraft(artifacts = emptyList(), capturedAtEpochMillis = 1_000L)

        val error = useCase(empty).exceptionOrNull()

        assertTrue("was $error", error is ScanSaveException.NothingToSave)
        assertEquals(emptyList<Any>(), repository.added)
    }

    @Test
    fun `an empty stored file is not catalogued`() = runTest {
        storage.sizeBytes = 0

        val error = useCase(draft()).exceptionOrNull()

        assertTrue("was $error", error is ScanSaveException.OutputFileEmpty)
        assertEquals(emptyList<Any>(), repository.added)
    }

    @Test
    fun `a storage failure is reported and nothing is catalogued`() = runTest {
        storage.storeFailure = ScanSaveException.OutputFileNotCopied()

        val error = useCase(draft()).exceptionOrNull()

        assertTrue("was $error", error is ScanSaveException.OutputFileNotCopied)
        assertEquals(emptyList<Any>(), repository.added)
    }

    // The file goes first, so it is already there when cataloguing fails. Left behind, it would be
    // storage the user cannot see or free.
    @Test
    fun `when cataloguing fails the stored file is removed and the failure is reported`() =
        runTest {
            repository.addFailure = IllegalStateException("database is full")

            val error = useCase(draft()).exceptionOrNull()

            assertEquals("database is full", error?.message)
            assertEquals(listOf("documents/new-uuid.pdf"), storage.deleted)
            assertEquals(emptyList<String>(), storage.files)
        }

    // The reason to report is why the save failed, not that the clean-up after it failed too.
    @Test
    fun `a clean-up that fails does not hide why the save failed`() = runTest {
        repository.addFailure = IllegalStateException("database is full")
        storage.deleteFailure = IllegalStateException("cannot delete")

        val error = useCase(draft()).exceptionOrNull()

        assertEquals("database is full", error?.message)
    }
}
