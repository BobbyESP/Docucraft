package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import android.net.Uri
import android.util.Log
import com.bobbyesp.docucraft.core.domain.repository.FileRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.ScannedDocumentsRepository
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.exists

/**
 * Use case for deleting a scanned PDF. Handles both database removal and physical file deletion.
 */
class DeleteDocumentUseCase(
    private val repository: ScannedDocumentsRepository,
    private val fileRepository: FileRepository,
) {
    suspend operator fun invoke(documentUri: Uri) {
        // Remove from database first (maintains referential integrity)
        repository.deleteDocument(documentUri)
        deleteFile(documentUri)
    }

    private suspend fun deleteFile(documentUri: Uri) {
        val filePath = when (documentUri.scheme) {
            "content" -> fileRepository.getFilePathFromUri(documentUri)
            "file" -> documentUri.path
            else -> {
                Log.w(TAG, "Unsupported URI scheme: ${documentUri.scheme}")
                return
            }
        }

        if (filePath.isNullOrBlank()) {
            Log.w(TAG, "Invalid file path from URI: $documentUri")
            return
        }

        try {
            val file = PlatformFile(filePath)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Successfully deleted file: $filePath")
            } else {
                Log.w(TAG, "File does not exist at path: $filePath")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "DeleteScannedPdfUseCase"
    }
}
