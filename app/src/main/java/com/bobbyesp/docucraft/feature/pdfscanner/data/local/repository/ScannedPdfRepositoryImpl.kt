package com.bobbyesp.docucraft.feature.pdfscanner.data.local.repository

import android.content.Context
import android.content.Intent
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
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.ScannedPdfRepository
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
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
    private val scannedPdfDao: ScannedPdfDao
) : ScannedPdfRepository {
    override suspend fun getAllScannedPdfsFlow(): Flow<List<ScannedPdf>> =
        scannedPdfDao.getAllPdfsFlow().map { entities ->
            entities.map { it.toModel() }
        }.flowOn(Dispatchers.IO)

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

    override suspend fun savePdf(
        scanPdfResult: GmsDocumentScanningResult.Pdf, filename: String
    ) {
        try {
            val outputDir = PlatformFile("${FileKit.filesDir}/scans/pdf")
            if (!outputDir.exists()) {
                outputDir.createDirectories(mustCreate = true)
            }

            val outputFile = PlatformFile(outputDir, "$filename.pdf")

            writePdf(scanPdfResult.uri, outputFile)

            val fileSizeBytes = outputFile.size()

            if (fileSizeBytes == -1L) throw IllegalStateException("The file size is undefined")

            val documentPath = FileProvider.getUriForFile(
                context, App.CONTENT_PROVIDER_AUTHORITY, File(outputFile.path)
            )

            val pdfEntity = ScannedPdfEntity(
                filename = filename,
                title = null,
                description = null,
                path = documentPath.toString(),
                createdTimestamp = System.currentTimeMillis(),
                fileSize = fileSizeBytes,
                pageCount = scanPdfResult.pageCount,
                thumbnail = null
            )
            scannedPdfDao.insert(pdfEntity)
        } catch (e: Exception) {
            throw RuntimeException("Failed to save PDF: ${e.message}", e)
        }
    }

    override suspend fun deletePdf(scannedPdf: ScannedPdf) {
        // TODO("Not yet implemented")
    }

    override fun sharePdf(pdfPath: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, pdfPath)
            setDataAndType(pdfPath, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(
                shareIntent, context.getString(R.string.share_pdf)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )

    }

    override fun openPdfInViewer(pdfPath: Uri) {
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setDataAndType(pdfPath, "application/pdf")
        }

        context.startActivity(
            Intent.createChooser(
                viewIntent, context.getString(R.string.open_pdf_in_viewer)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}