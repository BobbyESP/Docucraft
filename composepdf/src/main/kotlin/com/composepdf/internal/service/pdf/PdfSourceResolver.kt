package com.composepdf.internal.service.pdf

import android.content.Context
import android.os.ParcelFileDescriptor
import com.composepdf.PdfSource
import com.composepdf.RemotePdfState
import com.composepdf.internal.service.remote.RemotePdfLoader
import com.composepdf.internal.util.longLivedContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

/**
 * Resolves various [com.composepdf.PdfSource] types into a [android.os.ParcelFileDescriptor] suitable for use with
 * [android.graphics.pdf.PdfRenderer].
 *
 * This class handles the complexity of converting different source types (streams, byte arrays, URIs)
 * into file descriptors, managing temporary files as needed.
 *
 * The resolver implements [java.io.Closeable] and should be closed when the PDF document is no longer needed
 * to clean up any temporary files that were created.
 */
class PdfSourceResolver(context: Context) : Closeable {

    private val appContext = context.longLivedContext()
    // Track all temp files created by this resolver to ensure cleanup
    private val tempFiles = mutableListOf<File>()

    /**
     * Resolves the given [com.composepdf.PdfSource] to a [android.os.ParcelFileDescriptor].
     *
     * This operation may involve I/O operations (reading streams, copying assets, etc.)
     * and should be called from a background thread.
     *
     * @param source The PDF source to resolve
     * @return A [android.os.ParcelFileDescriptor] that can be used with [android.graphics.pdf.PdfRenderer]
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
                    // Use buffered output for better IO performance
                    BufferedOutputStream(FileOutputStream(file)).use { output ->
                        input.copyTo(output)
                    }
                }
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }

            is PdfSource.Bytes -> {
                val file = createTempFile()
                BufferedOutputStream(FileOutputStream(file)).use { output ->
                    output.write(source.bytes)
                }
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }

            is PdfSource.Stream -> {
                val file = createTempFile()
                source.streamProvider().use { input ->
                    BufferedOutputStream(FileOutputStream(file)).use { output ->
                        input.copyTo(output)
                    }
                }
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }

            is PdfSource.Uri -> {
                try {
                    appContext.contentResolver.openFileDescriptor(source.uri, "r")
                        ?: throw FileNotFoundException("ContentResolver returned null for: ${source.uri}")
                } catch (e: SecurityException) {
                    throw SecurityException("Permission denied opening URI: ${source.uri}", e)
                } catch (e: Exception) {
                    throw java.io.IOException("Failed to open PDF from URI: ${source.uri}", e)
                }
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

    suspend fun resolveRemote(
        source: PdfSource.Remote,
        onStateChange: (RemotePdfState) -> Unit = {}
    ): ParcelFileDescriptor = withContext(Dispatchers.IO) {
        val loader = RemotePdfLoader(appContext)
        var targetFile: File? = null
        var loadError: Throwable? = null

        // Collect the flow to track state and capture result/error
        try {
            loader.load(source).collect { state ->
                onStateChange(state)
                when (state) {
                    is RemotePdfState.Cached -> targetFile = state.file
                    is RemotePdfState.Error -> loadError = state.cause ?: Exception(state.message) // Use description if cause is null
                    else -> {}
                }
            }
        } catch (e: Exception) {
            throw java.io.IOException("Error during remote PDF load flow", e)
        }

        if (loadError != null) {
            throw java.io.IOException("Remote PDF failed to load", loadError)
        }

        val cachedFile = targetFile
            ?: throw IllegalStateException("Remote PDF loading sequence finished without producing a cached file.")

        ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun createTempFile(): File {
        return File.createTempFile("pdf_", ".pdf", appContext.cacheDir).also {
            tempFiles.add(it)
        }
    }

    /**
     * Cleans up any *temporary* files created during resolution (e.g. from streams/assets).
     * Does NOT delete files from the persistent remote cache.
     */
    override fun close() {
        val iterator = tempFiles.iterator()
        while (iterator.hasNext()) {
            val file = iterator.next()
            try {
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {
                // Best effort deletion
            }
            iterator.remove()
        }
    }
}