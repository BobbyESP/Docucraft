package com.bobbyesp.docucraft.feature.docscanner.data.local.repository

import androidx.activity.result.ActivityResult
import com.bobbyesp.docucraft.mlkit.domain.error.OperationFailure
import com.bobbyesp.docucraft.mlkit.domain.model.Document
import com.bobbyesp.docucraft.mlkit.domain.datsource.MlKitDataSource
import com.bobbyesp.docucraft.mlkit.domain.repository.DocumentScannerService
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository implementation that uses ML Kit for document scanning. It delegates the actual
 * processing to the DataSource and ensures thread safety.
 */
class MlKitDocumentScannerService(private val dataSource: MlKitDataSource) :
    DocumentScannerService {

    override suspend fun scanDocument(input: Any): Result<Document> {
        return withContext(Dispatchers.IO) {
            try {
                if (input !is ActivityResult) {
                    throw IllegalArgumentException("Input must be an ActivityResult")
                }

                val scannerResult =
                    GmsDocumentScanningResult.fromActivityResultIntent(input.data)
                        ?: throw OperationFailure.ScanCancelled(
                            "Scan was cancelled by the user. No result retrieved."
                        )

                val document = dataSource.processDocumentScanResult(scannerResult)
                Result.success(document)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}