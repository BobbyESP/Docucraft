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
 * Use case for deleting a scanned PDF. Handles both database removal and physical file deletion.
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

    private suspend fun deletePhysicalFile(pdfPath: Uri) {
        when (pdfPath.scheme) {
            "content" -> {
                val filePath = fileRepository.getFilePathFromUri(pdfPath)
                if (filePath != null) {
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
                } else {
                    // Fallback: delete using ContentResolver
                    try {
                        val rowsDeleted = context.contentResolver.delete(pdfPath, null, null)
                        Log.d(TAG, "ContentResolver deleted $rowsDeleted rows")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting via ContentResolver: ${e.message}", e)
                    }
                }
            }
            "file" -> {
                val filePath = pdfPath.path
                if (!filePath.isNullOrBlank()) {
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
                } else {
                    Log.w(TAG, "Invalid file path from URI: $pdfPath")
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
