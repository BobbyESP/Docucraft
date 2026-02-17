package com.bobbyesp.docucraft.feature.docscanner.data.local.service

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.graphics.createBitmap
import com.bobbyesp.docucraft.feature.docscanner.domain.service.DocumentOperationsService
import java.io.File
import java.io.FileOutputStream

/**
 * Implementation of PdfDocumentService. Handles PDF document operations like extracting pages as
 * images.
 */
class DocumentOperationsServiceImpl(private val context: Context) : DocumentOperationsService {
    companion object {
        private const val TAG = "PdfDocumentServiceImpl"
    }

    override fun saveDocumentPageAsImage(
        documentUri: Uri,
        outputFile: File,
        pageIndex: Int,
        format: Bitmap.CompressFormat,
        quality: Int,
    ) {
        var parcelFileDescriptor: ParcelFileDescriptor? = null
        try {
            parcelFileDescriptor =
                when (documentUri.scheme) {
                    ContentResolver.SCHEME_FILE -> {
                        val path = documentUri.path
                        if (path.isNullOrEmpty()) {
                            Log.e(TAG, "Empty path in file URI")
                            return
                        }
                        ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
                    }

                    ContentResolver.SCHEME_CONTENT -> {
                        Log.i(TAG, "Opened file descriptor for content URI: $documentUri")
                        context.contentResolver.openFileDescriptor(documentUri, "r")
                    }

                    else -> {
                        Log.e(TAG, "Unsupported URI scheme: ${documentUri.scheme}")
                        return
                    }
                }

            if (parcelFileDescriptor == null) {
                Log.e(TAG, "Error getting file descriptor for document")
                return
            }

            // Make sure parent directory exists
            outputFile.parentFile?.mkdirs()

            Log.d(TAG, "Output file path: ${outputFile.absolutePath}")

            PdfRenderer(parcelFileDescriptor).use { renderer ->
                if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                    Log.e(
                        TAG,
                        "Page index $pageIndex out of bounds. Total pages: ${renderer.pageCount}",
                    )
                    return
                }

                renderer.openPage(pageIndex).use { page ->
                    val bitmap = createBitmap(page.width, page.height)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    try {
                        if (!outputFile.exists() && !outputFile.createNewFile()) {
                            Log.e(TAG, "Failed to create output file at ${outputFile.absolutePath}")
                            return
                        }

                        FileOutputStream(outputFile).use { fos ->
                            if (!bitmap.compress(format, quality, fos)) {
                                Log.e(
                                    TAG,
                                    "Failed to compress bitmap to ${outputFile.absolutePath}",
                                )
                            } else {
                                Log.d(TAG, "Successfully saved image to ${outputFile.absolutePath}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error writing bitmap: ${e.message}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing PDF: ${e.message}", e)
        } finally {
            try {
                parcelFileDescriptor?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing file descriptor: ${e.message}", e)
            }
        }
    }
}
