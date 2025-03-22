package com.bobbyesp.docucraft.feature.pdfscanner.data.local.repository

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.graphics.createBitmap
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.PdfDocumentHelper
import java.io.File
import java.io.FileOutputStream

class PdfDocumentHelperImpl(private val context: Context) : PdfDocumentHelper {
    companion object {
        private const val TAG = "PdfDocumentHelperImpl"
    }

    override fun savePdfPageAsImage(
        pdfUri: Uri,
        outputFile: File,
        pageIndex: Int,
        format: Bitmap.CompressFormat,
        quality: Int,
    ) {
        var parcelFileDescriptor: ParcelFileDescriptor? = null
        try {
            parcelFileDescriptor =
                when (pdfUri.scheme) {
                    ContentResolver.SCHEME_FILE -> {
                        // If it's a file URI, we can use the traditional File approach
                        val path = pdfUri.path
                        if (path.isNullOrEmpty()) {
                            Log.e(TAG, "Empty path in file URI")
                            return
                        }
                        ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
                    }

                    ContentResolver.SCHEME_CONTENT -> {
                        Log.i(TAG, "Opened file descriptor for content URI: $pdfUri")
                        // If it's a content URI, we need to use the ContentResolver
                        context.contentResolver.openFileDescriptor(pdfUri, "r")
                    }

                    else -> {
                        Log.e(TAG, "Unsupported URI scheme: ${pdfUri.scheme}")
                        return
                    }
                }

            if (parcelFileDescriptor == null) {
                Log.e(TAG, "Error getting file descriptor for pdf")
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
                    // Create a bitmap with appropriate dimensions
                    val bitmap = createBitmap(page.width, page.height)
                    // Render the PDF page onto the bitmap
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    try {
                        // Ensure we're using a file path, not a content URI
                        if (!outputFile.exists() && !outputFile.createNewFile()) {
                            Log.e(TAG, "Failed to create output file at ${outputFile.absolutePath}")
                            return
                        }

                        // Write the bitmap to the output file
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
