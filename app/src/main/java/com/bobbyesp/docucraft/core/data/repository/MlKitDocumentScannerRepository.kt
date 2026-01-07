package com.bobbyesp.docucraft.core.data.repository

import android.app.Activity
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.bobbyesp.docucraft.core.data.datasource.MlKitDataSource
import com.bobbyesp.docucraft.core.domain.error.DomainError
import com.bobbyesp.docucraft.core.domain.model.ScannedDocument
import com.bobbyesp.docucraft.core.domain.repository.DocumentScannerRepository
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository implementation that uses ML Kit for document scanning. It delegates the actual
 * processing to the DataSource and ensures thread safety.
 */
class MlKitDocumentScannerRepository(private val dataSource: MlKitDataSource) :
    DocumentScannerRepository {

    override suspend fun scanDocument(input: Any): Result<ScannedDocument> {
        return withContext(Dispatchers.IO) {
            try {
                if (input !is ActivityResult) {
                    throw IllegalArgumentException("Input must be an ActivityResult")
                }

                val scannerResult =
                    GmsDocumentScanningResult.fromActivityResultIntent(input.data)
                        ?: throw DomainError.ScanCancelled(
                            "Scan was cancelled by the user. No result retrieved."
                        )

                val document = dataSource.processScanningResult(scannerResult)
                Result.success(document)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override fun launchScanner(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
    ) {
        Log.i("MlKitDocumentScannerRepo", "Launching document scanner")
        val options =
            GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(100)
                .setResultFormats(
                    GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                    GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
                )
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()

        val scanner = GmsDocumentScanning.getClient(options)

        scanner
            .getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                launcher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener {
                Log.e(
                    "MlKitDocumentScannerRepo",
                    "Error launching document scanner: ${it.message}",
                    it,
                )
                // Handle failure (maybe log it)
            }
    }
}
