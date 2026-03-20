package com.composepdf.internal.service.pdf

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.Size
import com.composepdf.PdfSource
import com.composepdf.internal.util.longLivedContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "PdfDocumentManager"

class PdfDocumentManager(context: Context) : Closeable {

    private val appContext = context.longLivedContext()

    private var masterFd: ParcelFileDescriptor? = null
    private var sourceResolver: PdfSourceResolver? = null

    private var rendererChannel: Channel<PdfRenderer>? = null

    private val maxParallelRenderers = 2
    private val generation = AtomicInteger(0)

    private var _pageCount = 0
    val pageCount: Int get() = _pageCount
    val isOpen: Boolean get() = masterFd != null

    suspend fun open(source: PdfSource) = withContext(Dispatchers.IO) {
        closeInternal()
        val resolver = PdfSourceResolver(appContext)
        sourceResolver = resolver
        try {
            val fd = resolver.resolve(source)
            masterFd = fd

            val firstRenderer = PdfRenderer(fd)
            _pageCount = firstRenderer.pageCount

            val channel = Channel<PdfRenderer>(maxParallelRenderers)
            channel.send(firstRenderer)

            repeat(maxParallelRenderers - 1) {
                try {
                    val dupFd = ParcelFileDescriptor.dup(fd.fileDescriptor)
                    channel.send(PdfRenderer(dupFd))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to dup FD for parallel rendering: ${e.message}")
                }
            }
            rendererChannel = channel
        } catch (t: Throwable) {
            closeInternal()
            throw t
        }
    }

    suspend fun <T> withPage(pageIndex: Int, action: suspend (PdfRenderer.Page) -> T): T {
        val currentChannel = rendererChannel ?: throw IllegalStateException("Document not open")
        val capturedGeneration = generation.get()

        val renderer = try {
            currentChannel.receive()
        } catch (e: Exception) {
            throw CancellationException("Document closed while waiting for renderer", e)
        }
        
        return try {
            if (generation.get() != capturedGeneration) {
                 throw CancellationException("Document closed (generation mismatch)")
            }
            
            renderer.openPage(pageIndex).use { page -> action(page) }
        } finally {
            if (generation.get() == capturedGeneration) {
                val result = currentChannel.trySend(renderer)
                if (result.isFailure) {
                     safeCloseRenderer(renderer)
                }
            } else {
                safeCloseRenderer(renderer)
            }
        }
    }

    suspend fun getAllPageSizes(): List<Size> = coroutineScope {
        if (!isOpen) throw IllegalStateException("Document not open")
        (0 until _pageCount).map { index ->
            async(Dispatchers.IO) {
                withPage(index) { page -> Size(page.width, page.height) }
            }
        }.awaitAll()
    }

    override fun close() {
        try {
           generation.incrementAndGet()
           closeParams()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing document manager", e)
        }
    }

    private fun closeInternal() {
        generation.incrementAndGet()
        closeParams()
    }

    private fun closeParams() {
        val channel = rendererChannel
        rendererChannel = null

        // Close channel to stop new consumers
        channel?.close()
        
        // Drain and close existing renderers
        if (channel != null) {
            // Try to receive all available items
            var renderer = channel.tryReceive().getOrNull()
            while (renderer != null) {
                safeCloseRenderer(renderer)
                renderer = channel.tryReceive().getOrNull()
            }
        }

        // Clean up FDs
        closeFdAndResolver()
        _pageCount = 0
    }

    private fun safeCloseRenderer(renderer: PdfRenderer) {
        try {
            renderer.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close renderer: ${e.message}")
        }
    }

    private fun closeFdAndResolver() {
        try {
            masterFd?.close()
        } catch (e: Exception) {
             Log.d(TAG, "Master FD close: ${e.message}")
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
