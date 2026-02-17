package com.bobbyesp.docucraft.mlkit.domain.repository

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.bobbyesp.docucraft.mlkit.domain.model.Document

/**
 * Interface for the Document Scanner. This abstracts the underlying implementation (e.g., Google ML
 * Kit, OpenCV).
 */
interface DocumentScannerService {
    /**
     * Scans a document from a given input source. The input type is generic to allow different
     * implementations (Bitmap, ImageProxy, Uri).
     *
     * @param input The image source to be analyzed.
     * @return A Resource containing the ScannedDocument if successful.
     */
    suspend fun scanDocument(input: Any): Result<Document>

    /**
     * Launches the scanner UI.
     *
     * @param activity The Activity instance required to start the scan intent.
     * @param launcher The ActivityResultLauncher to handle the result.
     */
    fun launchScanner(activity: Activity, launcher: ActivityResultLauncher<IntentSenderRequest>)
}