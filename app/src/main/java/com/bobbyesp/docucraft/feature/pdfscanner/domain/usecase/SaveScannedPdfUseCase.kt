package com.bobbyesp.docucraft.feature.pdfscanner.domain.usecase

import android.content.Context
import android.util.Log
import androidx.core.content.FileProvider
import com.bobbyesp.docucraft.App
import com.bobbyesp.docucraft.core.util.ensure
import com.bobbyesp.docucraft.feature.pdfscanner.data.local.db.entity.ScannedPdfEntity
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.ScannedPdfRepository
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.size
import java.io.File

/**
 * Use case for saving a scanned PDF from GMS scanner result.
 * Handles: file copying, thumbnail generation, and database persistence.
 * This is a coordinator use case that orchestrates multiple operations.
 */
class SaveScannedPdfUseCase(
    private val context: Context,
    private val repository: ScannedPdfRepository,
    private val copyPdfFileUseCase: CopyPdfFileUseCase,
    private val generatePdfThumbnailUseCase: GeneratePdfThumbnailUseCase,
) {
    suspend operator fun invoke(
        scanPdfResult: GmsDocumentScanningResult.Pdf,
        filename: String,
    ) {
        // Create output directory
        val pdfOutputDir = PlatformFile(FileKit.filesDir, "scans/pdf")
        pdfOutputDir.ensure(mustCreate = true)
        val pdfOutputFile = PlatformFile(pdfOutputDir, "$filename.pdf")

        // Copy the scanned PDF to internal storage
        copyPdfFileUseCase(scanPdfResult.uri, pdfOutputFile)

        // Validate file size
        val fileSizeBytes = pdfOutputFile.size()
        require(fileSizeBytes > 0) { "The file size is invalid." }

        // Get content URI using FileProvider
        val documentUri = FileProvider.getUriForFile(
            context,
            App.CONTENT_PROVIDER_AUTHORITY,
            File(pdfOutputFile.path),
        )

        // Generate thumbnail
        val thumbnailPath = try {
            generatePdfThumbnailUseCase(documentUri, filename)
        } catch (e: Exception) {
            Log.w(TAG, "Error generating thumbnail: ${e.message}")
            null
        }

        // Create entity and save to database
        val pdfEntity = ScannedPdfEntity(
            filename = filename,
            title = null,
            description = null,
            path = documentUri.toString(),
            createdTimestamp = System.currentTimeMillis(),
            fileSize = fileSizeBytes,
            pageCount = scanPdfResult.pageCount,
            thumbnail = thumbnailPath,
        )

        repository.savePdf(pdfEntity)
    }

    companion object {
        private const val TAG = "SaveScannedPdfUseCase"
    }
}
