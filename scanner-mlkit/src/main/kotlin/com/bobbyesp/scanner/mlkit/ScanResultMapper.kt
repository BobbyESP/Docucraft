/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.scanner.mlkit

import com.bobbyesp.scanner.ContentRef
import com.bobbyesp.scanner.ScanArtifact
import com.bobbyesp.scanner.ScanDraft
import com.bobbyesp.scanner.ScanError
import com.bobbyesp.scanner.ScanOutcome

/**
 * Decides what the raw values reported by a scanner engine mean.
 *
 * Deliberately free of framework and engine types: the caller extracts the values from whatever the
 * engine returned, and this decides the outcome. That split is what makes the decision table
 * testable on the JVM, which is where the cancellation and empty-result bugs were hiding.
 */
object ScanResultMapper {

    /**
     * @param completed Whether the engine reported a finished scan. `false` means the user backed
     *   out or the system dismissed the scanner, which is a cancellation and not a failure.
     * @param pdfUri Where the produced PDF lives, or `null` if the engine produced none.
     * @param pageCount Pages in the produced PDF, if the engine reported it.
     * @param pageUris Where the produced page images live, if the engine produced any.
     * @param capturedAtEpochMillis When the capture finished.
     */
    fun toOutcome(
        completed: Boolean,
        pdfUri: String?,
        pageCount: Int?,
        pageUris: List<String>?,
        capturedAtEpochMillis: Long,
    ): ScanOutcome {
        if (!completed) return ScanOutcome.Cancelled

        val artifacts = buildList {
            if (!pdfUri.isNullOrBlank()) {
                add(ScanArtifact.Pdf(content = ContentRef(pdfUri), pageCount = pageCount ?: 0))
            }

            val pages = pageUris.orEmpty().filter { it.isNotBlank() }
            if (pages.isNotEmpty()) add(ScanArtifact.Pages(pages.map(::ContentRef)))
        }

        // A finished scan that produced nothing is a real failure. Reporting it as a success with
        // an empty location only moves the error down to the copy step, where it surfaces as an
        // unrelated message.
        return if (artifacts.isEmpty()) {
            ScanOutcome.Failed(ScanError.NoOutputProduced)
        } else {
            ScanOutcome.Completed(ScanDraft(artifacts, capturedAtEpochMillis))
        }
    }
}
