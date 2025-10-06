package com.bobbyesp.docucraft.feature.pdfscanner.domain.usecase

import android.content.Context
import android.net.Uri
import android.util.Log
import com.bobbyesp.docucraft.core.domain.repository.FileRepository
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.ScannedPdfRepository
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.exists

/**
 * Use case for deleting a scanned PDF.
 * Handles both database removal and physical file deletion.
 */
class DeleteScannedPdfUseCase(
    private val context: Context,
    private val repository: ScannedPdfRepository,
    private val fileRepository: FileRepository,
) {
    suspend operator fun invoke(pdfPath: Uri) {
        // Remove from database first (maintains referential integrity)
        repository.deletePdf(pdfPath)

        // Delete physical file
        deletePhysicalFile(pdfPath)
    }

    private fun deletePhysicalFile(pdfPath: Uri) {
        when (pdfPath.scheme) {
            "content" -> {
                val filePath = fileRepository.getFilePathFromUri(pdfPath)
                if (filePath != null) {
                    val file = PlatformFile(filePath)
                    if (file.exists()) {
                        file.delete()
                        Log.d(TAG, "Successfully deleted file: $filePath")
                    } else {
                        Log.w(TAG, "File does not exist at path: $filePath")
                    }
                } else {
                    // Fallback: delete using ContentResolver
                    val rowsDeleted = context.contentResolver.delete(pdfPath, null, null)
                    Log.d(TAG, "ContentResolver deleted $rowsDeleted rows")
                }
            }
            "file" -> {
                val file = PlatformFile(pdfPath.path ?: "")
                if (file.exists()) {
                    file.delete()
                    Log.d(TAG, "Successfully deleted file: ${file.path}")
                } else {
                    Log.w(TAG, "File does not exist at path: ${file.path}")
                }
            }
            else -> {
                Log.w(TAG, "Unsupported URI scheme: ${pdfPath.scheme}")
            }
        }
    }

    companion object {
        private const val TAG = "DeleteScannedPdfUseCase"
    }
}

