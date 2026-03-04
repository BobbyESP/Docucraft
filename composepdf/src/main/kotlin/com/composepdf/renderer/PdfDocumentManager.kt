package com.composepdf.renderer

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.Size
import com.composepdf.source.PdfSource
import com.composepdf.source.PdfSourceResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.concurrent.ConcurrentLinkedQueue

private const val TAG = "PdfDocumentManager"

/**
 * Manages a pool of [PdfRenderer] instances to allow parallel rendering of pages and tiles.
 *
 * Android's [PdfRenderer] is thread-safe but internally synchronized, meaning it only
 * allows one rendering operation at a time. To achieve true multi-threaded rendering,
 * this manager duplicates the file descriptor and creates multiple renderer instances.
 */
class PdfDocumentManager(private val context: Context) : Closeable {

    private var masterFd: ParcelFileDescriptor? = null
    private var sourceResolver: PdfSourceResolver? = null
    private val rendererPool = ConcurrentLinkedQueue<PdfRenderer>()

    // Limits the number of parallel renderers based on CPU cores.
    private val maxParallelConfig = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(2, 8)
    private var semaphore = Semaphore(1) // Default to 1, updated after opening document

    private var _pageCount = 0
    val pageCount: Int get() = _pageCount
    val isOpen: Boolean get() = masterFd != null

    /** Opens the PDF document and initializes the renderer pool. */
    suspend fun open(source: PdfSource) = withContext(Dispatchers.IO) {
        closeInternal()
        val resolver = PdfSourceResolver(context)
        sourceResolver = resolver
        try {
            val fd = resolver.resolve(source)
            masterFd = fd

            val firstRenderer = PdfRenderer(fd)
            _pageCount = firstRenderer.pageCount
            rendererPool.offer(firstRenderer)

            var actualRenderers = 1
            // Duplicate FD to allow real multi-threaded access
            repeat(maxParallelConfig - 1) {
                try {
                    val dupFd = ParcelFileDescriptor.dup(fd.fileDescriptor)
                    rendererPool.offer(PdfRenderer(dupFd))
                    actualRenderers++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to dup FD for parallel rendering: ${e.message}")
                }
            }
            // Update semaphore to match the number of successfully created renderers
            semaphore = Semaphore(actualRenderers)
        } catch (t: Throwable) {
            closeInternal()
            throw t
        }
    }

    /** Executes an action on a specific page using an available renderer from the pool. */
    suspend fun <T> withPage(pageIndex: Int, action: (PdfRenderer.Page) -> T): T {
        return semaphore.withPermit {
            val renderer =
                rendererPool.poll() ?: throw IllegalStateException("PDF Renderer pool exhausted")
            try {
                renderer.openPage(pageIndex).use { page ->
                    action(page)
                }
            } finally {
                rendererPool.offer(renderer)
            }
        }
    }

    /** Retrieves the dimensions of all pages in the document. */
    suspend fun getAllPageSizes(): List<Size> = withContext(Dispatchers.IO) {
        if (!isOpen) return@withContext emptyList()
        semaphore.withPermit {
            val renderer =
                rendererPool.poll() ?: throw IllegalStateException("PDF Renderer pool exhausted")
            try {
                (0 until _pageCount).map { index ->
                    renderer.openPage(index).use { Size(it.width, it.height) }
                }
            } finally {
                rendererPool.offer(renderer)
            }
        }
    }

    override fun close() = closeInternal()

    private fun closeInternal() {
        while (rendererPool.isNotEmpty()) {
            try {
                rendererPool.poll()?.close()
            } catch (e: Exception) {
            }
        }
        try {
            masterFd?.close()
        } catch (e: Exception) {
        }
        masterFd = null
        try {
            sourceResolver?.close()
        } catch (e: Exception) {
        }
        sourceResolver = null
        _pageCount = 0
        semaphore = Semaphore(1)
    }
}
