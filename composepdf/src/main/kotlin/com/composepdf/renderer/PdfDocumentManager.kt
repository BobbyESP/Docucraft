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

class PdfDocumentManager(private val context: Context) : Closeable {

    private var masterFd: ParcelFileDescriptor? = null
    private var sourceResolver: PdfSourceResolver? = null
    private val rendererPool = ConcurrentLinkedQueue<PdfRenderer>()
    
    // Detectamos núcleos reales. Reservamos 1 para el sistema.
    private val maxParallel = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(4, 8)
    private val semaphore = Semaphore(maxParallel)

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
            
            // Abrir el documento múltiples veces para permitir acceso multihilo real
            repeat(maxParallel - 1) {
                try {
                    val dupFd = ParcelFileDescriptor.dup(fd.fileDescriptor)
                    rendererPool.offer(PdfRenderer(dupFd))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to dup FD for parallel rendering")
                }
            }
        } catch (t: Throwable) {
            closeInternal()
            throw t
        }
    }

    suspend fun <T> withPage(pageIndex: Int, action: (PdfRenderer.Page) -> T): T {
        return semaphore.withPermit {
            val renderer = rendererPool.poll() ?: throw IllegalStateException("No renderer")
            try {
                // Ejecutar en el pool de despacho nativo
                renderer.openPage(pageIndex).use { page ->
                    action(page)
                }
            } finally {
                rendererPool.offer(renderer)
            }
        }
    }

    suspend fun getAllPageSizes(): List<Size> = withPage(0) { _ ->
        val renderer = rendererPool.peek() ?: throw IllegalStateException("No renderer")
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
