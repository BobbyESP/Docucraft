package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import android.content.Context
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.bobbyesp.docucraft.App
import com.bobbyesp.docucraft.mlkit.domain.model.Document
import com.bobbyesp.docucraft.core.util.ensure
import com.bobbyesp.docucraft.feature.docscanner.data.local.db.entity.ScannedDocumentEntity
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.ScannedDocumentsRepository
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.size
import java.io.File

/**
 * Use case for saving a scanned PDF from GMS scanner result. Handles: file copying, thumbnail
 * generation, and database persistence. This is a coordinator use case that orchestrates multiple
 * operations.
 */
class SaveScannedDocumentUseCase(
    private val context: Context,
    private val repository: ScannedDocumentsRepository,
    private val copyDocumentToFileUseCase: CopyDocumentToFileUseCase,
    private val generateDocumentThumbnailUseCase: GenerateDocumentThumbnailUseCase,
) {
    suspend operator fun invoke(document: Document, filename: String) {
        // Create output directory
        val pdfOutputDir = PlatformFile(FileKit.filesDir, "scans/pdf")
        pdfOutputDir.ensure(mustCreate = true)
        val pdfOutputFile = PlatformFile(pdfOutputDir, "$filename.pdf")

        // Copy the scanned PDF to internal storage
        val sourceUri = document.uriString.toUri()
        copyDocumentToFileUseCase(sourceUri, pdfOutputFile)

        // Validate file size
        val fileSizeBytes = pdfOutputFile.size()
        require(fileSizeBytes > 0) { "The file size is invalid." }

        // Get content URI using FileProvider
        val documentUri =
            FileProvider.getUriForFile(context, App.getAuthority(context), File(pdfOutputFile.path))

        // Generate thumbnail
        val thumbnailPath =
            try {
                generateDocumentThumbnailUseCase(documentUri, filename)
            } catch (e: Exception) {
                Log.w(TAG, "Error generating thumbnail: ${e.message}")
                null
            }

        // Create entity and save to database
        val pdfEntity =
            ScannedDocumentEntity(
                filename = filename,
                title = null,
                description = null,
                path = documentUri.toString(),
                createdTimestamp = document.timestamp,
                fileSize = fileSizeBytes,
                pageCount = document.pageCount,
                thumbnail = thumbnailPath,
            )

        repository.saveDocument(pdfEntity)
    }

    suspend operator fun invoke(scanPdfResult: GmsDocumentScanningResult.Pdf, filename: String) {
        // Create output directory
        val pdfOutputDir = PlatformFile(FileKit.filesDir, "scans/pdf")
        pdfOutputDir.ensure(mustCreate = true)
        val pdfOutputFile = PlatformFile(pdfOutputDir, "$filename.pdf")

        // Copy the scanned PDF to internal storage
        copyDocumentToFileUseCase(scanPdfResult.uri, pdfOutputFile)

        // Validate file size
        val fileSizeBytes = pdfOutputFile.size()
        require(fileSizeBytes > 0) { "The file size is invalid." }

        // Get content URI using FileProvider
        val documentUri =
            FileProvider.getUriForFile(context, App.getAuthority(context), File(pdfOutputFile.path))

        // Generate thumbnail
        val thumbnailPath =
            try {
                generateDocumentThumbnailUseCase(documentUri, filename)
            } catch (e: Exception) {
                Log.w(TAG, "Error generating thumbnail: ${e.message}")
                null
            }

        // Create entity and save to database
        val pdfEntity =
            ScannedDocumentEntity(
                filename = filename,
                title = null,
                description = null,
                path = documentUri.toString(),
                createdTimestamp = System.currentTimeMillis(),
                fileSize = fileSizeBytes,
                pageCount = scanPdfResult.pageCount,
                thumbnail = thumbnailPath,
            )

        repository.saveDocument(pdfEntity)
    }

    companion object {
        private const val TAG = "SaveScannedPdfUseCase"
    }
}
