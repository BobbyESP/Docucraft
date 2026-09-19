/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.mapper

import com.bobbyesp.docucraft.feature.docscanner.domain.exception.ScannerException
import com.bobbyesp.docucraft.feature.docscanner.domain.scanner.ScanArtifact
import com.bobbyesp.docucraft.feature.docscanner.domain.scanner.ScanError
import com.bobbyesp.docucraft.feature.docscanner.domain.scanner.ScanOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decision table every scanner engine result goes through. Two of these cases are regression
 * tests for bugs the engine coupling was hiding (see `docs/architecture/01-scanner-analysis.md`).
 */
class ScanResultMapperTest {

    @Test
    fun `a finished scan carrying a pdf maps to a result`() {
        val scan =
            ScanResultMapper.map(
                    completed = true,
                    pdfUri = "content://scan/1.pdf",
                    pageCount = 3,
                    timestamp = 1_000L,
                )
                .getOrThrow()

        assertEquals("content://scan/1.pdf", scan.uri)
        assertEquals(3, scan.pageCount)
        assertEquals(1_000L, scan.timestamp)
    }

    /**
     * Regression test for B1: cancelling used to be thrown inside the repository's own `try` and
     * caught by it, arriving at the UI as [ScannerException.ScanFailed].
     */
    @Test
    fun `an unfinished scan is a cancellation, not a failure`() {
        val error =
            ScanResultMapper.map(
                    completed = false,
                    pdfUri = "content://scan/1.pdf",
                    pageCount = 3,
                    timestamp = 1_000L,
                )
                .exceptionOrNull()

        assertTrue(
            "expected a cancellation but was $error",
            error is ScannerException.ScanCancelled,
        )
    }

    /**
     * Regression test for B3: a finished scan without a PDF used to succeed with an empty Uri, and
     * only blew up later in the copy step with an unrelated message.
     */
    @Test
    fun `a finished scan without a pdf is a failure`() {
        val error =
            ScanResultMapper.map(
                    completed = true,
                    pdfUri = null,
                    pageCount = null,
                    timestamp = 1_000L,
                )
                .exceptionOrNull()

        assertTrue("expected a failure but was $error", error is ScannerException.ScanFailed)
    }

    @Test
    fun `a blank pdf location is a failure`() {
        val error =
            ScanResultMapper.map(
                    completed = true,
                    pdfUri = "   ",
                    pageCount = 1,
                    timestamp = 1_000L,
                )
                .exceptionOrNull()

        assertTrue("expected a failure but was $error", error is ScannerException.ScanFailed)
    }

    @Test
    fun `an unreported page count falls back to zero`() {
        val scan =
            ScanResultMapper.map(
                    completed = true,
                    pdfUri = "content://scan/1.pdf",
                    pageCount = null,
                    timestamp = 1_000L,
                )
                .getOrThrow()

        assertEquals(0, scan.pageCount)
    }

    @Test
    fun `cancellation wins over a missing pdf`() {
        val error =
            ScanResultMapper.map(
                    completed = false,
                    pdfUri = null,
                    pageCount = null,
                    timestamp = 1_000L,
                )
                .exceptionOrNull()

        assertTrue(
            "expected a cancellation but was $error",
            error is ScannerException.ScanCancelled,
        )
    }

    // ---------------- toOutcome (the shape the DocumentScanner port speaks) ----------------

    @Test
    fun `a finished scan becomes a draft carrying its pdf`() {
        val outcome =
            ScanResultMapper.toOutcome(
                completed = true,
                pdfUri = "content://scan/1.pdf",
                pageCount = 3,
                pageUris = null,
                capturedAtEpochMillis = 1_000L,
            )

        val draft = (outcome as ScanOutcome.Completed).draft
        assertEquals(1_000L, draft.capturedAtEpochMillis)
        assertEquals("content://scan/1.pdf", draft.pdf?.content?.value)
        assertEquals(3, draft.pdf?.pageCount)
    }

    @Test
    fun `page images are carried alongside the pdf`() {
        val outcome =
            ScanResultMapper.toOutcome(
                completed = true,
                pdfUri = "content://scan/1.pdf",
                pageCount = 2,
                pageUris = listOf("content://scan/p1.jpg", "content://scan/p2.jpg"),
                capturedAtEpochMillis = 1_000L,
            )

        val draft = (outcome as ScanOutcome.Completed).draft
        assertEquals(2, draft.artifacts.size)
        assertEquals(
            listOf("content://scan/p1.jpg", "content://scan/p2.jpg"),
            draft.pages?.contents?.map { it.value },
        )
    }

    /** An engine asked for images only still produces a usable draft. */
    @Test
    fun `page images alone are enough for a draft`() {
        val outcome =
            ScanResultMapper.toOutcome(
                completed = true,
                pdfUri = null,
                pageCount = null,
                pageUris = listOf("content://scan/p1.jpg"),
                capturedAtEpochMillis = 1_000L,
            )

        val draft = (outcome as ScanOutcome.Completed).draft
        assertEquals(null, draft.pdf)
        assertTrue(draft.pages is ScanArtifact.Pages)
    }

    @Test
    fun `an unfinished scan is cancelled, with nothing to report`() {
        val outcome =
            ScanResultMapper.toOutcome(
                completed = false,
                pdfUri = "content://scan/1.pdf",
                pageCount = 3,
                pageUris = null,
                capturedAtEpochMillis = 1_000L,
            )

        assertEquals(ScanOutcome.Cancelled, outcome)
    }

    @Test
    fun `a finished scan that produced nothing fails`() {
        val outcome =
            ScanResultMapper.toOutcome(
                completed = true,
                pdfUri = null,
                pageCount = null,
                pageUris = emptyList(),
                capturedAtEpochMillis = 1_000L,
            )

        assertEquals(ScanError.NoOutputProduced, (outcome as ScanOutcome.Failed).error)
    }
}
