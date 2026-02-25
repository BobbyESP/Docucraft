package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import com.bobbyesp.docucraft.feature.docscanner.domain.exception.DocumentExportFailure
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.copyTo
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.path

class ExportDocumentUseCase() {
    suspend operator fun invoke(scannedDocument: ScannedDocument): Result<Uri> {
        val androidDocumentsDirectory = PlatformFile(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        )

        val dir = PlatformFile(androidDocumentsDirectory, "Docucraft")

        val file = FileKit.openFileSaver(
            suggestedName = scannedDocument.title ?: scannedDocument.filename,
            extension = "pdf",
            directory = dir,
        ) ?: run {
            return Result.failure(
                DocumentExportFailure.Cancelled()
            )
        }

        val internalDocument = PlatformFile(scannedDocument.path)

        try {
            internalDocument.copyTo(file)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        if(!file.exists()) {
            return Result.failure(
                DocumentExportFailure.Unknown()
            )
        }

        return Result.success(
            file.path.toUri()
        )
    }
}