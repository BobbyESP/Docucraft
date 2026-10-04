/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File

/**
 * Opens and uses the platform's [PdfRenderer] in a way Android 7 survives. **Every `PdfRenderer` in
 * the app is opened through here**, never with its constructor.
 *
 * Android 7 (API 24 and 25) has two faults in its renderer, and each one crashes the process in
 * native code, where nothing can catch it. Android 8 fixed both, and needs none of this.
 *
 * ### A document that cannot be opened breaks the ones that can
 *
 * The platform counts the renderers that are using its PDF library: it starts the library for the
 * first and shuts it down after the last. A renderer that fails to open a document, because it is
 * protected with a password, damaged or not a PDF at all, is counted out twice: once when the open
 * fails, and once more when the half-built object is finalized, because its finalizer closes a
 * document it never had.
 *
 * From then on the count is one short. Depending on what else is open, the library is shut down
 * under a document that is still on screen, or is not started for the next one. Opening one
 * protected PDF and then any other document was enough.
 *
 * The extra count cannot be prevented, since the object that causes it is never handed over. It is
 * offset instead. Before a document is opened, a tiny document of the app's own is opened first. If
 * the real one opens, the tiny one is closed again and nothing has changed. If it fails, the tiny
 * one is never closed: it is one user the library still counts, which is exactly what the finalizer
 * is about to take away.
 *
 * ### Two documents cannot be drawn at once
 *
 * The PDF library is not made for two threads. Android 8 lets one call into it at a time, across
 * the whole process; Android 7 lets them all in. Two renderers drawing text at the same moment, the
 * viewer's own two or a viewer and a thumbnail, share the library's font cache and corrupt it.
 *
 * So below Android 8 every call into a renderer or one of its pages (opening, drawing, closing)
 * takes one lock, as the platform itself does from Android 8 on. [use] does it for a renderer that
 * is opened, used and closed in one go; the engine does it for the renderers it keeps.
 */
object PdfRenderers {

    /**
     * Opens [descriptor] as a PDF, for a renderer that is kept. The renderer owns the descriptor
     * from then on and closes it with itself; when this throws, the descriptor is the caller's to
     * close.
     *
     * Opening is all this protects. A renderer that is kept below Android 8 must make its own calls
     * one at a time with every other renderer's, which only the engine does: anything else opens,
     * uses and closes inside [use].
     *
     * @throws SecurityException if the document is protected with a password.
     * @throws java.io.IOException if it is not a PDF, or is damaged.
     */
    fun open(context: Context, descriptor: ParcelFileDescriptor): PdfRenderer {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return PdfRenderer(descriptor)

        // Under the lock for the count as much as for the library: the count a guard offsets is
        // the platform's, and shared.
        return exclusively {
            val guard = Guard.open(context)
            val renderer =
                try {
                    PdfRenderer(descriptor)
                } catch (failure: Throwable) {
                    guard?.keepForever()
                    throw failure
                }
            guard?.close()
            renderer
        }
    }

    /**
     * Opens [descriptor] as a PDF, hands the renderer to [block] and closes it, with nothing else
     * drawing in between on the versions where that matters. Fails as [open] fails.
     *
     * Keep [block] to what needs the renderer: below Android 8 no other page is drawn, anywhere in
     * the app, until it returns.
     */
    inline fun <T> use(
        context: Context,
        descriptor: ParcelFileDescriptor,
        block: (PdfRenderer) -> T,
    ): T = exclusively { open(context, descriptor).use(block) }

    /**
     * Runs [block] with no other call into the PDF library under way. Below Android 8 only: from
     * there on the platform does it itself, call by call.
     */
    @PublishedApi
    internal inline fun <T> exclusively(block: () -> T): T =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) block() else synchronized(lock, block)

    @PublishedApi internal val lock = Any()

    /** The tiny document, open, and the descriptor it was opened with. */
    private class Guard(
        private val renderer: PdfRenderer,
        private val descriptor: ParcelFileDescriptor,
    ) {

        fun close() = renderer.close()

        /**
         * Leaves the document counted for as long as the process lives. Held so that it is never
         * collected: a guard that was finalized would be closed, and give back the count it is
         * there to hold.
         *
         * Its file is let go, though. Nothing reads this document again, and a descriptor held for
         * every document that ever failed to open would run the process out of them.
         */
        fun keepForever() {
            kept += renderer
            runCatching { descriptor.close() }
        }

        companion object {

            /**
             * `null` when the document cannot be written or opened, in which case the open goes
             * ahead without it: no worse than opening the renderer directly.
             */
            fun open(context: Context): Guard? {
                val file = File(context.cacheDir, FILE)
                return try {
                    // Written again whenever it is not exactly what it should be: the cache can be
                    // emptied at any time, and a guard that failed to open would be one more
                    // failed renderer.
                    val document = MinimalPdf.bytes
                    if (!file.exists() || file.length() != document.size.toLong()) {
                        file.writeBytes(document)
                    }
                    val descriptor =
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    Guard(PdfRenderer(descriptor), descriptor)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not open the guard document", e)
                    null
                }
            }

            private val kept = ArrayList<PdfRenderer>()

            private const val FILE = "composepdf-renderer-guard.pdf"
        }
    }

    private const val TAG = "PdfRenderers"
}

/** The smallest document the platform opens: one empty page. */
internal object MinimalPdf {

    val bytes: ByteArray by lazy {
        val objects =
            listOf(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 72 72] >>",
            )

        val document = StringBuilder("%PDF-1.4\n")
        // The cross-reference table says at which byte each object starts, so the offsets are
        // taken as the document is written. Everything is ASCII: a character is a byte.
        val offsets = objects.mapIndexed { index, body ->
            document.length.also { document.append("${index + 1} 0 obj\n$body\nendobj\n") }
        }

        val tableAt = document.length
        document.append("xref\n0 ${objects.size + 1}\n")
        document.append("0000000000 65535 f \n")
        for (offset in offsets) {
            // Padded by hand: a format would write the digits of the device's language.
            document.append(offset.toString().padStart(10, '0')).append(" 00000 n \n")
        }
        document.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\n")
        document.append("startxref\n$tableAt\n%%EOF\n")

        document.toString().toByteArray(Charsets.US_ASCII)
    }
}
