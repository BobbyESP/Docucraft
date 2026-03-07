package com.composepdf.renderer

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.Size
import com.composepdf.source.PdfSource
import com.composepdf.source.PdfSourceResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

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

    // Set to 2 to match the scheduler's thread count.
    // Having more renderers than threads causes contention on the native side without speed gain.
    private val maxParallelRenderers = 2
    private var semaphore = Semaphore(1)
    private var currentPermits = 1

    /**
     * Incremented on every close cycle. [withPage] captures the generation before acquiring
     * the permit and discards the renderer on return if the generation changed, preventing
     * zombie renderers from leaking into a new session.
     */
    private val generation = AtomicInteger(0)

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

            val firstRenderer = PdfRenderer(fd)
            _pageCount = firstRenderer.pageCount
            rendererPool.offer(firstRenderer)

            var actualRenderers = 1
            repeat(maxParallelRenderers - 1) {
                try {
                    val dupFd = ParcelFileDescriptor.dup(fd.fileDescriptor)
                    rendererPool.offer(PdfRenderer(dupFd))
                    actualRenderers++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to dup FD for parallel rendering: ${e.message}")
                }
            }
            semaphore = Semaphore(actualRenderers)
            currentPermits = actualRenderers
        } catch (t: Throwable) {
            closeInternal()
            throw t
        }
    }

    suspend fun <T> withPage(pageIndex: Int, action: (PdfRenderer.Page) -> T): T {
        val capturedGeneration = generation.get()
        return semaphore.withPermit {
            val renderer =
                rendererPool.poll() ?: run {
                    Log.e(
                        TAG,
                        "Inconsistent PdfDocumentManager state: acquired semaphore permit but no PdfRenderer available. " +
                                "Renderer pool size=${rendererPool.size}."
                    )
                    throw IllegalStateException(
                        "Inconsistent PdfDocumentManager state: acquired permit but no PdfRenderer available"
                    )
                }
            try {
                renderer.openPage(pageIndex).use { page -> action(page) }
            } finally {
                if (generation.get() == capturedGeneration) {
                    rendererPool.offer(renderer)
                } else {
                    try {
                        renderer.close()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to close stale renderer: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Reads the width and height (in PDF points) for every page in the document.
     *
     * Pages are fetched concurrently using all available renderer slots in the pool,
     * so this is significantly faster than sequential reads on large documents.
     * Must be called after a successful [open].
     *
     * @return List of [Size] objects in page-index order.
     */
    suspend fun getAllPageSizes(): List<Size> = coroutineScope {
        if (!isOpen) throw IllegalStateException("Document not open")
        (0 until _pageCount).map { index ->
            async(Dispatchers.IO) {
                withPage(index) { page -> Size(page.width, page.height) }
            }
        }.awaitAll()
    }

    /**
     * Synchronous close (Closeable). Bumps the generation so in-flight [withPage] calls
     * discard their renderer instead of returning it to the pool.
     */
    override fun close() {
        generation.incrementAndGet()
        drainAndClosePool()
        closeFdAndResolver()
        _pageCount = 0
        currentPermits = 1
        semaphore = Semaphore(1)
    }

    /**
     * Suspend-safe close. Acquires all semaphore permits first — suspending, not blocking —
     * so every in-flight [withPage] has finished and returned its renderer to the pool
     * before we drain it. Generation is bumped after acquiring to ensure the pool is full.
     */
    private suspend fun closeInternal() {
        val permitsToAcquire = currentPermits
        val previousSemaphore = semaphore
        repeat(permitsToAcquire) { previousSemaphore.acquire() }
        try {
            generation.incrementAndGet()
            drainAndClosePool()
            closeFdAndResolver()
            _pageCount = 0
            currentPermits = 1
        } finally {
            repeat(permitsToAcquire) { previousSemaphore.release() }
        }
        semaphore = Semaphore(1)
    }

    private fun drainAndClosePool() {
        while (true) {
            val renderer = rendererPool.poll() ?: break
            try {
                renderer.close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to close renderer: ${e.message}")
            }
        }
    }

    private fun closeFdAndResolver() {
        try {
            masterFd?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close master FD: ${e.message}")
        }
        masterFd = null
        try {
            sourceResolver?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close source resolver: ${e.message}")
        }
        sourceResolver = null
    }
}
