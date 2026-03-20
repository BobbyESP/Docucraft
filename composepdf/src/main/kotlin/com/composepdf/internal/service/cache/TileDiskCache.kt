package com.composepdf.internal.service.cache

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.composepdf.internal.service.cache.bitmap.BitmapPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.random.Random

/**
 * Persistent LRU disk cache for high-resolution PDF tiles.
 *
 * Keys are the same strings used by [com.composepdf.PdfViewerState] for in-memory
 * tile storage: `"pageIndex_left_top_right_bottom_zoom"`, optionally prefixed with a
 * document identifier to avoid collisions when loading different PDFs.
 *
 * Writes are atomic (temp file + rename) to prevent corrupt cache entries.
 * LRU eviction respects actual access order by updating [File.lastModified] on reads.
 */
class TileDiskCache(
    private val directory: File,
    private val maxSizeBytes: Long = 300L * 1024 * 1024,
) {
    init {
        directory.mkdirs()
    }

    private fun fileFor(docKey: String, pageIndex: Int, tileKey: String): File {

        val docDir = File(directory, docKey)
        val pageDir = File(docDir, "p$pageIndex")

        if (!pageDir.exists()) {
            pageDir.mkdirs()
        }

        val safe = tileKey.replace('/', '_')

        return File(pageDir, "$safe.webp")
    }

    /**
     * Reads a tile bitmap from disk.
     *
     * A fresh [BitmapFactory.Options] is created per call to avoid race conditions between
     * the render threads that call this concurrently. Pool reuse via `inBitmap` is intentionally
     * **not** used because:
     * - WebP decoding has strict size-matching requirements for `inBitmap`
     * - The pool's `recycle()` on eviction can invalidate a bitmap between `get()` and decode
     * - The SIGSEGV crash was caused by exactly this race
     *
     * The returned bitmap is mutable so it can later be returned to [BitmapPool] by the caller.
     */
    fun get(docKey: String, pageIndex: Int, tileKey: String): Bitmap? {
        val file = fileFor(docKey, pageIndex, tileKey)
        if (!file.exists()) return null
        runCatching {
            file.setLastModified(System.currentTimeMillis())
        }

        val options = BitmapFactory.Options().apply {
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return try {
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun put(docKey: String, pageIndex: Int, tileKey: String, bitmap: Bitmap) =
        withContext(Dispatchers.IO) {
            val file = fileFor(docKey, pageIndex, tileKey)
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
                if (!tmp.renameTo(file)) {
                    tmp.delete()
                } else {
                    if (Random.nextInt(10) == 0) trim()
                }
            } catch (_: IOException) {
                tmp.delete()
            }
        }

    fun containsKey(docKey: String, pageIndex: Int, tileKey: String): Boolean =
        fileFor(docKey, pageIndex, tileKey).exists()

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


