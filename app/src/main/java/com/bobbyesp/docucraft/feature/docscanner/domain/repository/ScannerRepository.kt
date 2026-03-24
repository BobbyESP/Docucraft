package com.bobbyesp.docucraft.feature.docscanner.domain.repository

import androidx.activity.result.ActivityResult
import com.bobbyesp.docucraft.feature.docscanner.domain.model.RawScanResult

/**
 * Repository interface responsible for handling and processing document scanning operations.
 *
 * This repository acts as a bridge between the document scanning interface (such as a system Activity)
 * and the domain layer, transforming raw activity results into structured scan data.
 */
interface ScannerRepository {
    /**
     * Processes the [ActivityResult] received from the document scanner activity.
     *
     * This function extracts the scanned document data from the activity result and
     * maps it to a [RawScanResult], handling any potential errors during the parsing process.
     *
     * @param result The [ActivityResult] returned by the scanner activity launcher.
     * @return A [Result] containing the [RawScanResult] if successful, or an exception on failure.
     */
    suspend fun processResult(result: ActivityResult): Result<RawScanResult>
}