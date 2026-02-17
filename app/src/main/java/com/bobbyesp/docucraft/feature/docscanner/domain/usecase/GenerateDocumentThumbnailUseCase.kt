package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import android.net.Uri
import com.bobbyesp.docucraft.core.util.ensure
import com.bobbyesp.docucraft.feature.docscanner.domain.service.DocumentOperationsService
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path
import java.io.File

/**
 * Use case for generating a thumbnail image from a PDF's first page. Single responsibility:
 * thumbnail generation.
 */
class GenerateDocumentThumbnailUseCase(private val documentOperationsService: DocumentOperationsService) {
    operator fun invoke(pdfUri: Uri, filename: String): String {
        val thumbnailDir = PlatformFile(FileKit.filesDir, "previews")
        thumbnailDir.ensure(mustCreate = true)
        val thumbnailFile = PlatformFile(thumbnailDir, "$filename.png")

        documentOperationsService.saveDocumentPageAsImage(
            documentUri = pdfUri,
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
