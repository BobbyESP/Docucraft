package com.bobbyesp.docucraft.feature.docscanner.data.local.repository

import android.app.Activity
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.bobbyesp.docucraft.mlkit.domain.error.OperationFailure
import com.bobbyesp.docucraft.mlkit.domain.model.Document
import com.bobbyesp.docucraft.mlkit.domain.datsource.MlKitDataSource
import com.bobbyesp.docucraft.mlkit.domain.repository.DocumentScannerService
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
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