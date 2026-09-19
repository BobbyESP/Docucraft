/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.service

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.graphics.createBitmap
import java.io.File
import java.io.FileOutputStream

/** Renders with the platform's own [PdfRenderer]. */
class DocumentOperationsServiceImpl(private val context: Context) : DocumentOperationsService {

    override fun saveDocumentPageAsImage(
        documentUri: Uri,
        outputFile: File,
        pageIndex: Int,
        format: Bitmap.CompressFormat,
        quality: Int,
    ): Boolean =
        try {
            openDescriptor(documentUri)?.use { descriptor ->
                render(descriptor, outputFile, pageIndex, format, quality)
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Could not render $documentUri", e)
            false
        }

    private fun openDescriptor(documentUri: Uri): ParcelFileDescriptor? =
        when (documentUri.scheme) {
            ContentResolver.SCHEME_FILE -> {
                val path = documentUri.path
                if (path.isNullOrEmpty()) {
                    Log.e(TAG, "Empty path in file URI")
                    null
                } else {
                    ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
                }
            }

            ContentResolver.SCHEME_CONTENT ->
                context.contentResolver.openFileDescriptor(
                    documentUri,
                    "r",
                )

            else -> {
                Log.e(TAG, "Unsupported URI scheme: ${documentUri.scheme}")
                null
            }
        }

    private fun render(
        descriptor: ParcelFileDescriptor,
        outputFile: File,
        pageIndex: Int,
        format: Bitmap.CompressFormat,
        quality: Int,
    ): Boolean =
        PdfRenderer(descriptor).use { renderer ->
            if (pageIndex !in 0 until renderer.pageCount) {
                Log.e(TAG, "Page $pageIndex out of bounds; the document has ${renderer.pageCount}")
                return false
            }

            renderer.openPage(pageIndex).use { page ->
                val bitmap = createBitmap(page.width, page.height)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                outputFile.parentFile?.mkdirs()

                FileOutputStream(outputFile).use { out ->
                    bitmap.compress(format, quality, out).also { written ->
                        if (!written) Log.e(TAG, "Could not encode ${outputFile.name} as $format")
                    }
                }
            }
        }

    private companion object {
        const val TAG = "DocumentOperations"
    }
}
