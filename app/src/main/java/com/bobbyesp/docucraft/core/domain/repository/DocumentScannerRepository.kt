package com.bobbyesp.docucraft.core.domain.repository

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.bobbyesp.docucraft.core.domain.model.ScannedDocument

/**
 * Interface for the Document Scanner.
 * This abstracts the underlying implementation (e.g., Google ML Kit, OpenCV).
 */
interface DocumentScannerRepository {
    /**
     * Scans a document from a given input source.
     * The input type is generic to allow different implementations (Bitmap, ImageProxy, Uri).
     *
     * @param input The image source to be analyzed.
     * @return A Resource containing the ScannedDocument if successful.
     */
    suspend fun scanDocument(input: Any): Result<ScannedDocument>

    /**
     * Launches the scanner UI.
     * @param activity The Activity instance required to start the scan intent.
     * @param launcher The ActivityResultLauncher to handle the result.
     */
    fun launchScanner(activity: Activity, launcher: ActivityResultLauncher<IntentSenderRequest>)
}
