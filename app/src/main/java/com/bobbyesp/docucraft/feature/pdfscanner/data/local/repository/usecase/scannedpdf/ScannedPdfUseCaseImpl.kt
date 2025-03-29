package com.bobbyesp.docucraft.feature.pdfscanner.data.local.repository.usecase.scannedpdf

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.bobbyesp.docucraft.App
import com.bobbyesp.docucraft.core.domain.repository.FileRepository
import com.bobbyesp.docucraft.core.util.ensure
import com.bobbyesp.docucraft.feature.pdfscanner.data.local.db.entity.ScannedPdfEntity
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.PdfDocumentHelper
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.ScannedPdfRepository
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.usecase.PdfFileManagementUseCase
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.usecase.ScannedPdfUseCase
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.size
import java.io.File
import kotlinx.coroutines.flow.Flow

class ScannedPdfUseCaseImpl(
    private val context: Context,
    private val repository: ScannedPdfRepository,
    private val fileRepository: FileRepository,
    private val pdfFileManagementUseCase: PdfFileManagementUseCase,
    private val pdfDocsHelper: PdfDocumentHelper,
) : ScannedPdfUseCase {

    override suspend fun scannedPdfsListFlow(): Flow<List<ScannedPdf>> {
        return repository.getAllScannedPdfsFlow()
    }

    override suspend fun searchPdfs(query: String): List<ScannedPdf> {
        return repository.searchPdfsByTitleOrDescription(query)
    }

    override suspend fun getScannedPdf(pdfId: String): ScannedPdf {
        return repository.getScannedPdfById(pdfId)
    }

    override suspend fun getScannedPdf(pdfPath: Uri): ScannedPdf {
        return repository.getScannedPdfByPath(pdfPath)
    }

    override suspend fun saveScannedPdf(
        scanPdfResult: GmsDocumentScanningResult.Pdf,
        filename: String,
    ) {
        val pdfOutputDir = PlatformFile(FileKit.filesDir, "scans/pdf")
        pdfOutputDir.ensure(mustCreate = true)
        val pdfOutputFile = PlatformFile(pdfOutputDir, "$filename.pdf")

        // Copy the scanned PDF to the app's internal storage (the desired output file)
        pdfFileManagementUseCase.copyToSystemStorage(scanPdfResult.uri, pdfOutputFile)

        // Validate the file size
        val fileSizeBytes = pdfOutputFile.size()
        if (fileSizeBytes == -1L) {
            throw IllegalStateException("The file size is undefined.")
        }

        // Get the content uri using the FileProvider
        val documentUri =
            FileProvider.getUriForFile(
                context,
                App.CONTENT_PROVIDER_AUTHORITY,
                File(pdfOutputFile.path),
            )

        // Generate a thumbnail for the scanned PDF
        val thumbnailPath = generateThumbnail(documentUri, filename)

        val pdfEntity =
            ScannedPdfEntity(
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

    private suspend fun generateThumbnail(pdfUri: Uri, filename: String): String? {
        val thumbnailDir = PlatformFile(FileKit.filesDir, "previews")
        thumbnailDir.ensure(mustCreate = true)
        val thumbnailFile = PlatformFile(thumbnailDir, "$filename.png")

        return try {
            pdfDocsHelper.savePdfPageAsImage(
                pdfUri = pdfUri,
                outputFile = File(thumbnailFile.path),
                pageIndex = 0,
                quality = 65,
            )
            thumbnailFile.path
        } catch (e: Exception) {
            Log.w("ScannedPdfUseCase", "Error generating thumbnail: ${e.message}")
            null
        }
    }

    override suspend fun modifyPdf(pdfId: String, title: String?, description: String?) {
        if (pdfId.isEmpty()) {
            throw IllegalArgumentException("The PDF ID cannot be empty.")
        }
        repository.modifyTitleAndDescription(pdfId, title, description)
    }

    override suspend fun deleteScannedPdf(pdfPath: Uri) {
        repository.deletePdf(pdfPath)
        // Then attempt to delete the physical file
        if (pdfPath.scheme == "content") {
            // For content URIs, try to extract the file path
            val filePath = fileRepository.getFilePathFromUri(pdfPath)
            if (filePath != null) {
                val file = PlatformFile(filePath)
                if (file.exists()) {
                    file.delete()
                    Log.d(TAG, "Successfully deleted file: $filePath")
                } else {
                    Log.w(TAG, "File does not exist at path: $filePath")
                    throw IllegalArgumentException(
                        "File does not exist at path: $filePath. Unable to delete."
                    )
                }
            } else {
                // Fallback to direct content resolver delete for content URI
                val rowsDeleted = context.contentResolver.delete(pdfPath, null, null)
                Log.d(TAG, "Content resolver delete result: $rowsDeleted rows affected")
            }
        } else {
            // For file URIs, convert to file path
            val file = PlatformFile(pdfPath.path ?: "")
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Successfully deleted file: ${file.path}")
            } else {
                Log.w(TAG, "File does not exist at path: ${file.path}")
                throw IllegalArgumentException(
                    "File does not exist at path: ${file.path}. Unable to delete."
                )
            }
        }
    }

    override fun sharePdf(pdfPath: Uri) {
        repository.sharePdf(pdfPath)
    }

    override fun openPdfInViewer(pdfPath: Uri) {
        repository.openPdfInViewer(pdfPath)
    }

    companion object {
        private const val TAG = "ScannedPdfUseCase"
    }
}
