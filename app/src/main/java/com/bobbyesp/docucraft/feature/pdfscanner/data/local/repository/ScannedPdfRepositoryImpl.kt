package com.bobbyesp.docucraft.feature.pdfscanner.data.local.repository

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.bobbyesp.docucraft.App
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.repository.FileRepository
import com.bobbyesp.docucraft.feature.pdfscanner.data.local.db.dao.ScannedPdfDao
import com.bobbyesp.docucraft.feature.pdfscanner.data.local.db.entity.ScannedPdfEntity
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf.Companion.toModel
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.PdfDocumentHelper
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.ScannedPdfRepository
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.sink
import io.github.vinceglb.filekit.size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.io.buffered
import java.io.File

class ScannedPdfRepositoryImpl(
    private val context: Context,
    private val fileRepository: FileRepository,
    private val scannedPdfDao: ScannedPdfDao,
    private val pdfDocsHelper: PdfDocumentHelper
) : ScannedPdfRepository {
    override suspend fun getAllScannedPdfsFlow(): Flow<List<ScannedPdf>> =
        scannedPdfDao.getAllPdfsFlow().map { entities -> entities.map { it.toModel() } }
            .flowOn(Dispatchers.IO)

    // TODO: Move this to FileRepository
    private suspend fun writePdf(inputUri: Uri, outputFile: PlatformFile) {
        // Ensure directory exists
        val parentDir = PlatformFile(outputFile.path.substringBeforeLast('/'))
        if (!parentDir.exists()) {
            parentDir.createDirectories(mustCreate = true)
        }

        val sink = outputFile.sink(append = false).buffered()

        sink.use { bufferedSink ->
            // Use contentResolver to open an input stream from the Uri
            context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                val buffer = ByteArray(8192) // 8KB buffer for efficient transfer
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    bufferedSink.write(buffer, 0, bytesRead)
                }
            } ?: throw IllegalStateException("Could not open input stream for URI: $inputUri")
        }
    }

    override suspend fun savePdf(scanPdfResult: GmsDocumentScanningResult.Pdf, filename: String) {
        try {
            // Create output directory for PDFs and ensure it exists
            val pdfOutputDir = PlatformFile(FileKit.filesDir, "scans/pdf")
            if (!pdfOutputDir.exists()) {
                pdfOutputDir.createDirectories(mustCreate = true)
            }
            val pdfOutputFile = PlatformFile(pdfOutputDir, "$filename.pdf")

            // Write the PDF file
            writePdf(scanPdfResult.uri, pdfOutputFile)

            // Validate file size
            val fileSizeBytes = pdfOutputFile.size()
            if (fileSizeBytes == -1L) {
                throw IllegalStateException("The file size is undefined")
            }

            // Get a content URI for the saved PDF file
            val documentUri = FileProvider.getUriForFile(
                context,
                App.CONTENT_PROVIDER_AUTHORITY,
                File(pdfOutputFile.path)
            )

            // Prepare the directory for the thumbnail image
            val thumbnailDir = PlatformFile(FileKit.filesDir, "previews")
            if (!thumbnailDir.exists()) {
                thumbnailDir.createDirectories(mustCreate = true)
            }
            val thumbnailFile = PlatformFile(thumbnailDir, "$filename.png")

            // Attempt to generate a thumbnail from the first page of the PDF
            val thumbnailPath: String? = try {
                pdfDocsHelper.savePdfPageAsImage(
                    pdfUri = scanPdfResult.uri,
                    outputFile = File(thumbnailFile.path),
                    pageIndex = 0,
                    format = Bitmap.CompressFormat.PNG
                )
                thumbnailFile.path
            } catch (e: Exception) {
                Log.w(TAG, "Failed to generate thumbnail: ${e.message}")
                null
            }

            // Create and insert the PDF entity into the database
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
            scannedPdfDao.insert(pdfEntity)
        } catch (e: Exception) {
            throw RuntimeException("Failed to save PDF: ${e.message}", e)
        }
    }

    override suspend fun deletePdf(pdfPath: Uri) {
        // First remove from database to maintain referential integrity
        val deletedCount = scannedPdfDao.deleteByPath(pdfPath.toString())

        if (deletedCount <= 0) {
            throw IllegalArgumentException("No PDF found with path: $pdfPath")
        }

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
                    throw IllegalArgumentException(
                        "File does not exist at path: $filePath. Unable to delete."
                    )
                    Log.w(TAG, "File does not exist at path: $filePath")
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
                throw IllegalArgumentException(
                    "File does not exist at path: ${file.path}. Unable to delete."
                )
                Log.w(TAG, "File does not exist at path: ${file.path}")
            }
        }
    }

    override fun sharePdf(pdfPath: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, pdfPath)
            setDataAndType(pdfPath, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(shareIntent, context.getString(R.string.share_pdf))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    override fun openPdfInViewer(pdfPath: Uri) {
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setDataAndType(pdfPath, "application/pdf")
        }

        context.startActivity(
            Intent.createChooser(viewIntent, context.getString(R.string.open_pdf_in_viewer))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    companion object {
        private const val TAG = "ScannedPdfRepository"
    }
}
