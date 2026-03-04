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
private const val MAX_PARALLEL_RENDERERS = 4

/**
 * High-performance PDF manager that supports parallel rendering using a pool of [PdfRenderer] instances.
 */
class PdfDocumentManager(private val context: Context) : Closeable {

    private var masterFd: ParcelFileDescriptor? = null
    private var sourceResolver: PdfSourceResolver? = null
    
    // Pool of renderers to allow true parallel processing
    private val rendererPool = ConcurrentLinkedQueue<PdfRenderer>()
    
    // Limits the number of concurrent render operations to prevent OOM and CPU saturation
    private val semaphore = Semaphore(MAX_PARALLEL_RENDERERS)

    private var _pageCount = 0
    val pageCount: Int get() = _pageCount

    val isOpen: Boolean get() = masterFd != null

    suspend fun open(source: PdfSource) = withContext(Dispatchers.IO) {
        closeInternal()
        val resolver = PdfSourceResolver(context)
        sourceResolver = resolver
        try {
            val fd = resolver.resolve(source)
            masterFd = fd
            
            // Create the first renderer to get page count
            val firstRenderer = PdfRenderer(fd)
            _pageCount = firstRenderer.pageCount
            rendererPool.offer(firstRenderer)
            
            // Pre-warm additional renderers by duplicating the file descriptor
            // This allows parallel access to the same file
            repeat(MAX_PARALLEL_RENDERERS - 1) {
                try {
                    val dupFd = ParcelFileDescriptor.dup(fd.fileDescriptor)
                    rendererPool.offer(PdfRenderer(dupFd))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to dup FD for parallel rendering", e)
                }
            }
        } catch (t: Throwable) {
            closeInternal()
            throw t
        }
    }

    /**
     * Executes a render action using an available renderer from the pool.
     * This allows multiple tiles/pages to render in parallel.
     */
    suspend fun <T> withPage(pageIndex: Int, action: (PdfRenderer.Page) -> T): T {
        return semaphore.withPermit {
            val renderer = rendererPool.poll() ?: throw IllegalStateException("Renderer pool exhausted")
            try {
                withContext(Dispatchers.Default) { // Use Default for CPU-bound rendering
                    renderer.openPage(pageIndex).use { page ->
                        action(page)
                    }
                }
            } finally {
                rendererPool.offer(renderer)
            }
        }
    }

    suspend fun getAllPageSizes(): List<Size> = withPage(0) { _ ->
        // We use one renderer from the pool to iterate sizes. 
        // This is metadata-heavy, so it's fast.
        val renderer = rendererPool.peek() ?: throw IllegalStateException("No renderer available")
        (0 until _pageCount).map { index ->
            renderer.openPage(index).use { Size(it.width, it.height) }
        }
    }

    override fun close() = closeInternal()

    private fun closeInternal() {
        while (rendererPool.isNotEmpty()) {
            try { rendererPool.poll()?.close() } catch (e: Exception) {}
        }
        try { masterFd?.close() } catch (e: Exception) {}
        masterFd = null
        try { sourceResolver?.close() } catch (e: Exception) {}
        sourceResolver = null
        _pageCount = 0
    }
}
