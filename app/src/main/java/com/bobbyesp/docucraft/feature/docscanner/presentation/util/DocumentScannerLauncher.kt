package com.bobbyesp.docucraft.feature.docscanner.presentation.util

import android.app.Activity
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning

/**
 * Handles the ML Kit Document Scanner intent creation and launching.
 * This belongs in the Presentation/UI layer because it deals directly with Android Activities and UI intents.
 */
object DocumentScannerLauncher {

    fun launch(activity: Activity, launcher: ActivityResultLauncher<IntentSenderRequest>) {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(100)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        val scanner = GmsDocumentScanning.getClient(options)

        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                launcher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener {
                Log.e("DocumentScannerLauncher", "Failed to start scan intent", it)
            }
    }
}
