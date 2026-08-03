/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.domain

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import java.io.FileOutputStream

/**
 * A [PrintDocumentAdapter] that streams an already-rendered PDF (identified by [uri]) straight into
 * the system print spooler. It does not re-render pages — the source is copied byte-for-byte, which
 * is the correct behaviour for printing an existing PDF document.
 *
 * @param context Used to resolve the [uri] through the [android.content.ContentResolver].
 * @param uri The PDF to print. May be a `content://` or `file://` URI.
 * @param jobName Human-readable name shown in the print UI.
 */
class PdfPrintDocumentAdapter(
    private val context: Context,
    private val uri: Uri,
    private val jobName: String,
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }

        val info =
            PrintDocumentInfo.Builder(jobName)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build()

        // Second argument = true: layout changed (we always report a fresh document).
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        try {
            val input =
                context.contentResolver.openInputStream(uri)
                    ?: run {
                        callback.onWriteFailed("Unable to open the document")
                        return
                    }

            input.use { source ->
                FileOutputStream(destination.fileDescriptor).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        if (cancellationSignal?.isCanceled == true) {
                            callback.onWriteCancelled()
                            return
                        }
                        val read = source.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }

            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.localizedMessage)
        }
    }
}
