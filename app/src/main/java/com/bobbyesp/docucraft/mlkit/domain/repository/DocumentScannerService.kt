package com.bobbyesp.docucraft.mlkit.domain.repository

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
}