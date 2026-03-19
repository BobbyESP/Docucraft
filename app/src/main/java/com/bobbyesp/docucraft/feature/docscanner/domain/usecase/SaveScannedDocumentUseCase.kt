package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.bobbyesp.docucraft.App
import com.bobbyesp.docucraft.core.util.DateTime
import com.bobbyesp.docucraft.core.util.ensure
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.ScannedDocumentEntity
import com.bobbyesp.docucraft.feature.docscanner.domain.exception.ScanSaveException
import com.bobbyesp.docucraft.feature.docscanner.domain.model.RawScanResult
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LocalDocumentsRepository
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Use case for saving a scanned PDF. Handles file copying, thumbnail generation, and database persistence.
 */
class SaveScannedDocumentUseCase(
    private val context: Context,
    private val repository: LocalDocumentsRepository,
    private val copyDocumentToFileUseCase: CopyDocumentToFileUseCase,
    private val generateDocumentThumbnailUseCase: GenerateDocumentThumbnailUseCase,
) {
    suspend operator fun invoke(rawScanResult: RawScanResult): Result<Uri> {
        val formattedTimestamp = DateTime.formatDateTime(
            timestampMillis = rawScanResult.timestamp,
            pattern = "yyyyMMdd_HHmmss"
        )

        return saveDocument(
            sourceUri = rawScanResult.uri.toUri(),
            filename = "Scan_${formattedTimestamp}",
            pageCount = rawScanResult.pageCount,
            timestamp = rawScanResult.timestamp
        )
    }


    suspend operator fun invoke(rawScanResult: RawScanResult, filename: String): Result<Uri> =
        saveDocument(
            sourceUri = rawScanResult.uri.toUri(),
            filename = filename,
            pageCount = rawScanResult.pageCount,
            timestamp = rawScanResult.timestamp
        )

    suspend operator fun invoke(
        scanPdfResult: GmsDocumentScanningResult.Pdf,
        filename: String
    ): Result<Uri> =
        saveDocument(
            sourceUri = scanPdfResult.uri,
            filename = filename,
            pageCount = scanPdfResult.pageCount,
            timestamp = System.currentTimeMillis()
        )

    private suspend fun saveDocument(
        sourceUri: Uri,
        filename: String,
        pageCount: Int,
        timestamp: Long
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val pdfOutputDir =
                PlatformFile(FileKit.filesDir, "scans/pdf").apply { ensure(mustCreate = true) }
            val pdfOutputFile = PlatformFile(pdfOutputDir, "$filename.pdf")

            copyDocumentToFileUseCase(sourceUri, pdfOutputFile).onFailure { error ->
                Log.e(TAG, "Error copying document to file: ${error.message}")
                throw ScanSaveException.OutputFileNotCopied()
            }

            val fileSizeBytes = pdfOutputFile.size()
            require(fileSizeBytes > 0) { "The file size is invalid." }

            val documentUri =
                FileProvider.getUriForFile(
                    context,
                    App.getAuthority(context),
                    File(pdfOutputFile.path)
                )

            val thumbnailPath = try {
                generateDocumentThumbnailUseCase(documentUri, filename)
            } catch (e: Exception) {
                Log.w(TAG, "Error generating thumbnail: ${e.message}")
                null
            }

            val pdfEntity = ScannedDocumentEntity(
                filename = filename,
                title = null,
                description = null,
                path = documentUri.toString(),
                createdTimestamp = timestamp,
                fileSize = fileSizeBytes,
                pageCount = pageCount,
                thumbnail = thumbnailPath,
            )

            repository.saveDocument(pdfEntity)
            Log.d(TAG, "Document saved successfully: $documentUri")

            documentUri
        }
    }

    companion object {
        private const val TAG = "SaveScannedPdfUseCase"
    }
}
