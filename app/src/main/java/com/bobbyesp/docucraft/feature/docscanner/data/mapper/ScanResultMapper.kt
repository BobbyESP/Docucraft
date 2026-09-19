/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.mapper

import com.bobbyesp.docucraft.feature.docscanner.domain.exception.ScannerException
import com.bobbyesp.docucraft.feature.docscanner.domain.model.RawScanResult
import com.bobbyesp.docucraft.feature.docscanner.domain.scanner.ContentRef
import com.bobbyesp.docucraft.feature.docscanner.domain.scanner.ScanArtifact
import com.bobbyesp.docucraft.feature.docscanner.domain.scanner.ScanDraft
import com.bobbyesp.docucraft.feature.docscanner.domain.scanner.ScanError
import com.bobbyesp.docucraft.feature.docscanner.domain.scanner.ScanOutcome

/**
 * Decides what the raw values reported by a scanner engine mean.
 *
 * Deliberately free of framework and engine types: the caller extracts the values from whatever the
 * engine returned, and this decides the outcome. That split is what makes the decision table
 * testable on the JVM, which is where the cancellation and empty-result bugs were hiding.
 */
object ScanResultMapper {

    private const val NO_DOCUMENT = "The scanner returned no document"

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

    /**
     * The same decision, in the shape the pre-[ScanOutcome] path still expects. Delegates rather
     * than repeating itself, so there is only ever one decision table to reason about.
     *
     * Goes away with the rest of the old path in step 6 of the migration plan.
     */
    fun map(
        completed: Boolean,
        pdfUri: String?,
        pageCount: Int?,
        timestamp: Long,
    ): Result<RawScanResult> =
        when (
            val outcome =
                toOutcome(
                    completed = completed,
                    pdfUri = pdfUri,
                    pageCount = pageCount,
                    pageUris = null,
                    capturedAtEpochMillis = timestamp,
                )
        ) {
            ScanOutcome.Cancelled -> Result.failure(ScannerException.ScanCancelled())

            is ScanOutcome.Failed -> Result.failure(ScannerException.ScanFailed(NO_DOCUMENT))

            is ScanOutcome.Completed ->
                outcome.draft.pdf?.let { pdf ->
                    Result.success(
                        RawScanResult(
                            uri = pdf.content.value,
                            pageCount = pdf.pageCount,
                            timestamp = timestamp,
                        )
                    )
                } ?: Result.failure(ScannerException.ScanFailed(NO_DOCUMENT))
        }
}
