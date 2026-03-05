package com.composepdf.cache

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Persistent LRU disk cache for high-resolution PDF tiles.
 *
 * Keys are the same strings used by [com.composepdf.state.PdfViewerState] for in-memory
 * tile storage: `"pageIndex_left_top_right_bottom_zoom"`, optionally prefixed with a
 * document identifier to avoid collisions when loading different PDFs.
 *
 * Writes are atomic (temp file + rename) to prevent corrupt cache entries.
 * LRU eviction respects actual access order by updating [File.lastModified] on reads.
 */
class TileDiskCache(
    private val directory: File,
    private val maxSizeBytes: Long = 300L * 1024 * 1024
) {
    init {
        directory.mkdirs()
    }

    private fun fileFor(tileKey: String): File {
        val safe = tileKey.replace('/', '_')
        return File(directory, "$safe.webp")
    }

    fun get(tileKey: String): Bitmap? {
        val file = fileFor(tileKey)
        if (!file.exists()) return null
        file.setLastModified(System.currentTimeMillis())
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    suspend fun put(tileKey: String, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        val file = fileFor(tileKey)
        val tmp = File("${file.absolutePath}.tmp")
        try {
            FileOutputStream(tmp).use { fos ->
                BufferedOutputStream(fos).use { out ->
                    val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSLESS
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }
                    bitmap.compress(format, 100, out)
                    out.flush()
                }
            }
            if (!tmp.renameTo(file)) tmp.delete() else trim()
        } catch (_: IOException) {
            tmp.delete()
        }
    }

    fun containsKey(tileKey: String): Boolean = fileFor(tileKey).exists()

    private fun trim() {
        val files = directory.listFiles() ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxSizeBytes) return
        files.sortedBy { it.lastModified() }.forEach { f ->
            total -= f.length()
            f.delete()
            if (total <= maxSizeBytes) return
        }
    }

    /** Removes all tiles for a specific document prefix (e.g. when a new PDF is loaded). */
    fun clearForDocument(docPrefix: String) {
        directory.listFiles { f -> f.name.startsWith(docPrefix) }?.forEach { it.delete() }
    }

    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }
}


