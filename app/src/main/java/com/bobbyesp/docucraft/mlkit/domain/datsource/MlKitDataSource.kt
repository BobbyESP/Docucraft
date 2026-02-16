package com.bobbyesp.docucraft.mlkit.domain.datsource

import com.bobbyesp.docucraft.mlkit.domain.model.ScannedDocument

/**
 * Interface for interacting with the ML Kit SDK. This isolates the ML Kit implementation details
 * from the Repository.
 */
interface MlKitDataSource {
    /**
     * Processes the result from the GmsDocumentScanner Activity.
     *
     * @param result The raw result object (expected to be GmsDocumentScanningResult).
     * @throws com.bobbyesp.docucraft.mlkit.domain.exception.ScannerException if the result type is invalid.
     */
    suspend fun processDocumentScanResult(result: Any): ScannedDocument
}