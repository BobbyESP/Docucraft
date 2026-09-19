/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.scanner.mlkit

import com.bobbyesp.scanner.ScanArtifact
import com.bobbyesp.scanner.ScanError
import com.bobbyesp.scanner.ScanOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decision table every scanner engine result goes through. Cancellation and the empty result
 * are regression tests for bugs the engine coupling was hiding (see
 * `docs/architecture/01-scanner-analysis.md`).
 */
class ScanResultMapperTest {

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
