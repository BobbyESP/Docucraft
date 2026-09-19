/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.mapper

import com.bobbyesp.docucraft.feature.docscanner.domain.exception.ScannerException
import com.bobbyesp.docucraft.feature.docscanner.domain.model.RawScanResult

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
     * @param pdfUri The location of the produced PDF, or `null` if the engine produced none.
     * @param pageCount Pages in the produced PDF, if the engine reported it.
     * @param timestamp When the scan was captured, in epoch milliseconds.
     */
    fun map(
        completed: Boolean,
        pdfUri: String?,
        pageCount: Int?,
        timestamp: Long,
    ): Result<RawScanResult> =
        when {
            !completed -> Result.failure(ScannerException.ScanCancelled())

            // A finished scan that produced nothing is a real failure. Reporting it as a success
            // with an empty location only moves the error down to the copy step, where it
            // surfaces as an unrelated message.
            pdfUri.isNullOrBlank() ->
                Result.failure(ScannerException.ScanFailed("The scanner returned no document"))

            else ->
                Result.success(
                    RawScanResult(uri = pdfUri, pageCount = pageCount ?: 0, timestamp = timestamp)
                )
        }
}
