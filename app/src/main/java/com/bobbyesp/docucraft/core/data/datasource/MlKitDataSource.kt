package com.bobbyesp.docucraft.core.data.datasource

import com.bobbyesp.docucraft.core.domain.exception.ScannerException
import com.bobbyesp.docucraft.core.domain.model.ScannedDocument

/**
 * Interface for interacting with the ML Kit SDK.
 * This isolates the ML Kit implementation details from the Repository.
 */
interface MlKitDataSource {
    /**
     * Processes the result from the GmsDocumentScanner Activity.
     * @param result The raw result object (expected to be GmsDocumentScanningResult).
     * @throws ScannerException if the result type is invalid.
     */
    suspend fun processScanningResult(result: Any): ScannedDocument
}
