package com.composepdf.renderer

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Size
import com.composepdf.source.PdfSource
import com.composepdf.source.PdfSourceResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable

/**
 * Thread-safe wrapper around [PdfRenderer] for loading and managing PDF documents.
 *
 * This class handles the lifecycle of the [PdfRenderer], providing thread-safe access
 * to page metadata and rendering operations. It uses a mutex to ensure only one thread
 * accesses the renderer at a time, as [PdfRenderer] is not thread-safe.
 *
 * Example usage:
 * ```kotlin
 * val manager = PdfDocumentManager(context)
 * manager.open(PdfSource.FromAsset("document.pdf"))
 *
 * val pageCount = manager.pageCount
 * val pageSize = manager.getPageSize(0)
 *
 * manager.close()
 * ```
 */
class PdfDocumentManager(private val context: Context) : Closeable {

    private var renderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var sourceResolver: PdfSourceResolver? = null

    private val mutex = Mutex()

    /**
     * The number of pages in the currently loaded document.
     * Returns 0 if no document is loaded.
     */
    val pageCount: Int
        get() = renderer?.pageCount ?: 0

    /**
     * Whether a document is currently loaded.
     */
    val isOpen: Boolean
        get() = renderer != null

    /**
     * Opens a PDF document from the given source.
     *
     * This operation is performed on [Dispatchers.IO] and is safe to call
     * from the main thread.
     *
     * @param source The PDF source to open
     * @throws java.io.IOException If the document cannot be opened
     */
    suspend fun open(source: PdfSource) = mutex.withLock {
        withContext(Dispatchers.IO) {
            // Close any previously opened document to free resources
            closeInternal()

            // Resolve the source to a file descriptor
            val resolver = PdfSourceResolver(context)
            // Assign resolver immediately so it can be closed if open fails
            sourceResolver = resolver

            try {
                val fd = resolver.resolve(source)
                // Assign fd immediately so it can be closed if renderer creation fails
                fileDescriptor = fd

                renderer = PdfRenderer(fd)
            } catch (t: Throwable) {
                // If opening fails or is cancelled, ensure we clean up partial state
                // (e.g. temporary files created by resolver, or open FD)
                closeInternal()
                throw t
            }
        }
    }

    /**
     * Gets the size of a specific page in points.
     *
     * @param pageIndex Zero-based page index
     * @return The page size in points (1/72 inch)
     * @throws IllegalStateException If no document is loaded
     * @throws IndexOutOfBoundsException If the page index is invalid
     */
    suspend fun getPageSize(pageIndex: Int): Size = mutex.withLock {
        withContext(Dispatchers.IO) {
            val pdfRenderer = renderer ?: throw IllegalStateException("No document is open")

            pdfRenderer.openPage(pageIndex).use { page ->
                Size(page.width, page.height)
            }
        }
    }

    /**
     * Returns the sizes of all pages in a single mutex acquisition.
     *
     * Prefer this over calling [getPageSize] in a loop, which would acquire
     * and release the mutex once per page (N round-trips on Dispatchers.IO).
     *
     * @return List of [Size] objects in document order
     * @throws IllegalStateException If no document is loaded
     */
    suspend fun getAllPageSizes(): List<Size> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val pdfRenderer = renderer ?: throw IllegalStateException("No document is open")
            (0 until pdfRenderer.pageCount).map { index ->
                pdfRenderer.openPage(index).use { page ->
                    Size(page.width, page.height)
                }
            }
        }
    }

    /**
     * Opens a page for rendering and executes the given action.
     *
     * The page is automatically closed after the action completes.
     * This method is thread-safe due to the internal mutex.
     *
     * @param pageIndex Zero-based page index
     * @param action The action to perform with the opened page
     * @return The result of the action
     * @throws IllegalStateException If no document is loaded
     */
    suspend fun <T> withPage(pageIndex: Int, action: (PdfRenderer.Page) -> T): T = mutex.withLock {
        withContext(Dispatchers.IO) {
            val pdfRenderer = renderer ?: throw IllegalStateException("No document is open")

            pdfRenderer.openPage(pageIndex).use { page ->
                action(page)
            }
        }
    }

    /**
     * Closes the current document and releases all resources.
     */
    override fun close() {
        closeInternal()
    }

    /**
     * Releases all resources held by the current document in dependency order:
     * renderer → file descriptor → source resolver.
     *
     * Each resource is closed in its own try/catch so that a failure on one
     * (e.g. the renderer is already closed) does not prevent the others from
     * being released, avoiding resource leaks.
     */
    private fun closeInternal() {
        // 1. Close renderer first (depends on FD)
        try {
            renderer?.close()
        } catch (e: Exception) {
            // Ignore close errors, usually strict mode or already closed
        }
        renderer = null

        // 2. Close file descriptor (depends on file/stream)
        try {
            fileDescriptor?.close()
        } catch (e: Exception) {
            // Ignore close errors
        }
        fileDescriptor = null

        // 3. Close resolver (deletes temp files)
        try {
            sourceResolver?.close()
        } catch (e: Exception) {
            // Ignore close errors
        }
        sourceResolver = null
    }
}
