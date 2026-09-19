/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.FakeDocumentStorage
import com.bobbyesp.docucraft.feature.docscanner.domain.exception.ScanSaveException
import com.bobbyesp.docucraft.feature.docscanner.domain.model.NewScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LocalDocumentsRepository
import com.bobbyesp.scanner.ContentRef
import com.bobbyesp.scanner.ScanArtifact
import com.bobbyesp.scanner.ScanDraft
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The save step used to be untestable: it reached for a Context, a FileProvider and the Room entity
 * directly, so nothing below an instrumented test could touch it. With storage behind a port there
 * is nothing Android-shaped left in it, and the interesting cases are all reachable.
 */
class SaveScanDraftUseCaseTest {

    private val storage = FakeDocumentStorage()
    private val repository = mockk<LocalDocumentsRepository>(relaxed = true)
    private val useCase = SaveScanDraftUseCase(storage, repository)

    private fun draft(pages: Int = 3, capturedAt: Long = 1_700_000_000_000L) =
        ScanDraft(
            artifacts = listOf(ScanArtifact.Pdf(ContentRef("content://scan/raw.pdf"), pages)),
            capturedAtEpochMillis = capturedAt,
        )

    private suspend fun catalogued(): NewScannedDocument {
        val captured = slot<NewScannedDocument>()
        coVerify { repository.saveDocument(capture(captured)) }
        return captured.captured
    }

    @Test
    fun `catalogues the stored document, not the one the scanner handed over`() = runTest {
        val result = useCase(draft(pages = 3), filename = "Invoice")

        assertEquals(ContentRef("content://stored/Invoice.pdf"), result.getOrThrow())

        val document = catalogued()
        assertEquals("Invoice", document.filename)
        assertEquals(ContentRef("content://stored/Invoice.pdf"), document.location)
        assertEquals(3, document.pageCount)
        assertEquals(1_024L, document.fileSizeBytes)
        assertEquals(1_700_000_000_000L, document.createdTimestamp)
        assertEquals(ContentRef("/previews/scan.png"), document.thumbnail)
    }

    @Test
    fun `derives a filename from the capture time when none is given`() = runTest {
        useCase(draft())

        // The exact stamp is the device's local time, so only its shape is worth asserting.
        val name = storage.usedFilename.orEmpty()
        assertTrue(name, name.startsWith("Scan_"))
        assertEquals("Scan_yyyyMMdd_HHmmss".length, name.length)
        assertEquals(name, catalogued().filename)
    }

    /** A document without a preview is still a document. */
    @Test
    fun `a thumbnail failure does not fail the save`() = runTest {
        storage.thumbnailFailure = IllegalStateException("cannot render")

        val result = useCase(draft())

        assertTrue(result.isSuccess)
        assertNull(catalogued().thumbnail)
    }

    @Test
    fun `a draft carrying no pdf is not catalogued`() = runTest {
        val empty = ScanDraft(artifacts = emptyList(), capturedAtEpochMillis = 1_000L)

        val error = useCase(empty).exceptionOrNull()

        assertTrue("was $error", error is ScanSaveException.NothingToSave)
        coVerify(exactly = 0) { repository.saveDocument(any()) }
    }

    @Test
    fun `an empty stored file is not catalogued`() = runTest {
        storage.sizeBytes = 0

        val error = useCase(draft()).exceptionOrNull()

        assertTrue("was $error", error is ScanSaveException.OutputFileEmpty)
        coVerify(exactly = 0) { repository.saveDocument(any()) }
    }

    @Test
    fun `a storage failure is reported and nothing is catalogued`() = runTest {
        storage.storeFailure = ScanSaveException.OutputFileNotCopied()

        val error = useCase(draft()).exceptionOrNull()

        assertTrue("was $error", error is ScanSaveException.OutputFileNotCopied)
        coVerify(exactly = 0) { repository.saveDocument(any()) }
    }
}
