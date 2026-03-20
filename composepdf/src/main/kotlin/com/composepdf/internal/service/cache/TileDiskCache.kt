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
 * A persistent disk-based cache for storing rendered PDF tile bitmaps.
 *
 * This cache organizes tiles using a hierarchical directory structure based on document keys
 * and page indices. It implements a Least Recently Used (LRU) eviction policy by updating
 * the file's last modified timestamp on every read and trimming the directory size when
 * it exceeds the specified threshold.
 *
 * Key features:
 * - **Atomic Writes**: Uses temporary files and renames to ensure cache integrity even if a write is interrupted.
 * - **WebP Compression**: Saves tiles in WebP format (lossless on Android R+) to minimize disk usage.
 * - **LRU Eviction**: Periodically trims the oldest files when the cache size exceeds [maxSizeBytes].
 * - **Concurrency Safe**: Designed to handle concurrent reads, while writes are handled via coroutines on the IO dispatcher.
 *
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
     * Retrieves a cached bitmap tile from the disk.
     *
     * This method updates the file's last modified timestamp to ensure it is treated as
     * "recently used" for the LRU eviction policy. The resulting bitmap is decoded as
     * a mutable [Bitmap] with [Bitmap.Config.ARGB_8888] configuration.
     *
     * @param docKey The unique identifier for the document.
     * @param pageIndex The zero-based index of the page.
     * @param tileKey The unique identifier for the specific tile.
     * @return The cached [Bitmap] if found and successfully decoded, or `null` otherwise.
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


