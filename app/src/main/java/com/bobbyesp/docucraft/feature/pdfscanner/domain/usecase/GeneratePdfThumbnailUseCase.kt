package com.bobbyesp.docucraft.feature.pdfscanner.domain.usecase

import android.net.Uri
import com.bobbyesp.docucraft.core.util.ensure
import com.bobbyesp.docucraft.feature.pdfscanner.domain.service.PdfDocumentService
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path
import java.io.File

/**
 * Use case for generating a thumbnail image from a PDF's first page.
 * Single responsibility: thumbnail generation.
 */
class GeneratePdfThumbnailUseCase(
    private val pdfDocumentService: PdfDocumentService
) {
    suspend operator fun invoke(pdfUri: Uri, filename: String): String {
        val thumbnailDir = PlatformFile(FileKit.filesDir, "previews")
        thumbnailDir.ensure(mustCreate = true)
        val thumbnailFile = PlatformFile(thumbnailDir, "$filename.png")

        pdfDocumentService.savePdfPageAsImage(
            pdfUri = pdfUri,
            outputFile = File(thumbnailFile.path),
            pageIndex = 0,
            quality = THUMBNAIL_QUALITY,
        )

        return thumbnailFile.path
    }

    companion object {
        private const val THUMBNAIL_QUALITY = 65
    }
}
package com.bobbyesp.docucraft.feature.pdfscanner.domain.usecase

import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.ScannedPdfRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for retrieving all scanned PDFs as a flow.
 * Follows Single Responsibility Principle - handles only one business action.
 */
class GetAllScannedPdfsUseCase(
    private val repository: ScannedPdfRepository
) {
    suspend operator fun invoke(): Flow<List<ScannedPdf>> {
        return repository.getAllScannedPdfsFlow()
    }
}
