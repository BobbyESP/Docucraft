/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.service

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.graphics.createBitmap
import com.composepdf.PdfRenderers
import java.io.File
import java.io.FileOutputStream

/** Renders with the platform's own [PdfRenderer]. */
class DocumentOperationsServiceImpl(private val context: Context) : DocumentOperationsService {

    override fun pageCount(document: File): Int? {
        // Asked of the file before the renderer is: one that is plainly not a PDF is answered
        // without opening anything.
        if (!document.startsAsAPdf()) return null

        return try {
            ParcelFileDescriptor.open(document, ParcelFileDescriptor.MODE_READ_ONLY).use {
                PdfRenderers.use(context, it) { renderer -> renderer.pageCount }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not count the pages of ${document.name}", e)
            null
        }
    }

    /** A PDF says so near its beginning: `%PDF-`, within its first kilobyte. */
    private fun File.startsAsAPdf(): Boolean =
        try {
            inputStream().use { input ->
                val head = ByteArray(PDF_HEADER_WINDOW)
                val read = input.read(head)
                read > 0 && String(head, 0, read, Charsets.ISO_8859_1).contains(PDF_HEADER)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read $name", e)
            false
        }

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
    ): Boolean {
        // Only the drawing is done with the renderer: nothing else in the app draws a page until
        // it is given back, on the versions that cannot draw two at once.
        val bitmap =
            PdfRenderers.use(context, descriptor) { renderer ->
                if (pageIndex !in 0 until renderer.pageCount) {
                    Log.e(
                        TAG,
                        "Page $pageIndex out of bounds; the document has ${renderer.pageCount}",
                    )
                    return false
                }

                renderer.openPage(pageIndex).use { page ->
                    createBitmap(page.width, page.height).also { bitmap ->
                        // The paper. A page paints only what is printed on it, so without this it
                        // comes out transparent and takes the colour of whatever the image is
                        // shown over.
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }

        outputFile.parentFile?.mkdirs()

        return FileOutputStream(outputFile).use { out ->
            bitmap.compress(format, quality, out).also { written ->
                if (!written) Log.e(TAG, "Could not encode ${outputFile.name} as $format")
            }
        }
    }

    private companion object {
        const val TAG = "DocumentOperations"
        const val PDF_HEADER = "%PDF-"
        const val PDF_HEADER_WINDOW = 1024
    }
}
