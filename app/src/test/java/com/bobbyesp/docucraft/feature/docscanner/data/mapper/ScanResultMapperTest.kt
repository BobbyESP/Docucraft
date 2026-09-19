/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.mapper

import com.bobbyesp.docucraft.feature.docscanner.domain.exception.ScannerException
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
}
