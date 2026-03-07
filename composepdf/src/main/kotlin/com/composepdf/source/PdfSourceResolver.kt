package com.composepdf.source

import android.content.Context
import android.os.ParcelFileDescriptor
import com.composepdf.util.longLivedContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream

/**
 * Resolves various [PdfSource] types into a [ParcelFileDescriptor] suitable for use with
 * [android.graphics.pdf.PdfRenderer].
 *
 * This class handles the complexity of converting different source types (streams, byte arrays, URIs)
 * into file descriptors, managing temporary files as needed.
 *
 * The resolver implements [Closeable] and should be closed when the PDF document is no longer needed
 * to clean up any temporary files that were created.
 */
class PdfSourceResolver(context: Context) : Closeable {

    private val appContext = context.longLivedContext()
    private var createdTempFile: File? = null

    /**
     * Resolves the given [PdfSource] to a [ParcelFileDescriptor].
     *
     * This operation may involve I/O operations (reading streams, copying assets, etc.)
     * and should be called from a background thread.
     *
     * @param source The PDF source to resolve
     * @return A [ParcelFileDescriptor] that can be used with [android.graphics.pdf.PdfRenderer]
     * @throws java.io.IOException If the source cannot be resolved
     */
    suspend fun resolve(source: PdfSource): ParcelFileDescriptor = withContext(Dispatchers.IO) {
        when (source) {
            is PdfSource.File -> {
                ParcelFileDescriptor.open(source.file, ParcelFileDescriptor.MODE_READ_ONLY)
            }

            is PdfSource.Asset -> {
                val file = createTempFile()
                appContext.assets.open(source.assetName).use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }

            is PdfSource.Bytes -> {
                val file = createTempFile()
                FileOutputStream(file).use { output ->
                    output.write(source.bytes)
                }
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }

            is PdfSource.Stream -> {
                val file = createTempFile()
                source.streamProvider().use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }

            is PdfSource.Uri -> {
                appContext.contentResolver.openFileDescriptor(source.uri, "r")
                    ?: throw IllegalArgumentException("Cannot open URI: ${source.uri}")
            }

            is PdfSource.Remote -> {
                // Remote sources are handled by RemotePdfLoader
                // This branch converts a pre-downloaded cached file to ParcelFileDescriptor
                throw IllegalStateException(
                    "Remote sources must be resolved through RemotePdfLoader first. " +
                            "Use resolveRemote() for PdfSource.Remote."
                )
            }
        }
    }

    /**
     * Resolves a remote [PdfSource.Remote] by downloading (or using cache) and returning a file descriptor.
     *
     * This method handles the full lifecycle of remote PDF loading:
     * 1. Check cache for existing file
     * 2. Download if not cached
     * 3. Return ParcelFileDescriptor for rendering
     *
     * @param source The remote PDF source
     * @param onStateChange Callback for state updates (progress, errors)
     * @return A [ParcelFileDescriptor] for the downloaded/cached PDF
     */
    suspend fun resolveRemote(
        source: PdfSource.Remote,
        onStateChange: (com.composepdf.remote.RemotePdfState) -> Unit = {}
    ): ParcelFileDescriptor = withContext(Dispatchers.IO) {
        val loader = com.composepdf.remote.RemotePdfLoader(appContext)
        var targetFile: File? = null

        loader.load(source).collect { state ->
            onStateChange(state)

            if (state is com.composepdf.remote.RemotePdfState.Cached) {
                targetFile = state.file
                // Do NOT assign to createdTempFile, as that would cause it to be deleted on close().
                // Cached files are managed by DiskCacheManager.
            }
        }

        val cachedFile = targetFile
            ?: throw IllegalStateException("Remote PDF loading did not produce a cached file")

        ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun createTempFile(): File {
        return File.createTempFile("pdf_", ".pdf", appContext.cacheDir).also {
            createdTempFile = it
        }
    }

    /**
     * Cleans up any *temporary* files created during resolution (e.g. from streams/assets).
     * Does NOT delete files from the persistent remote cache.
     */
    override fun close() {
        createdTempFile?.delete()
        createdTempFile = null
    }
}
