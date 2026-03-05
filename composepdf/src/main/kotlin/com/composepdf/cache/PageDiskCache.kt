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

class PageDiskCache(
    private val directory: File,
    private val maxSizeBytes: Long = 200L * 1024 * 1024 // 200MB
) {

    init {
        if (!directory.exists()) {
            directory.mkdirs()
        }
    }

    private fun fileFor(key: PageCacheKey): File {
        val name = "${key.pageIndex}_${key.zoomLevel}_${key.width}_${key.height}.webp"
        return File(directory, name)
    }

    fun get(key: PageCacheKey): Bitmap? {
        val file = fileFor(key)
        if (!file.exists()) return null
        file.setLastModified(System.currentTimeMillis())
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    /**
     * Saves a bitmap to the cache.
     * This is now a suspend function to ensure it's called from a coroutine.
     */
    suspend fun put(key: PageCacheKey, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        val file = fileFor(key)

        try {
            // Use a temporary file to ensure atomic writes (prevents corrupted cache files)
            val tmpFile = File("${file.absolutePath}.tmp")

            FileOutputStream(tmpFile).use { fos ->
                BufferedOutputStream(fos).use { out ->
                    // Use WEBP_LOSSLESS for high quality, or WEBP_LOSSY for better size/speed
                    val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSLESS
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }

                    bitmap.compress(format, 100, out)
                    out.flush() // Ensure all data is pushed to the stream
                }
            }

            // Atomically move the temp file to the actual destination
            if (tmpFile.renameTo(file)) {
                trim()
            } else {
                tmpFile.delete()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun trim() {
        val files = directory.listFiles() ?: return
        var total = files.sumOf { it.length() }

        if (total <= maxSizeBytes) return

        val sorted = files.sortedBy { it.lastModified() }

        for (file in sorted) {
            total -= file.length()
            file.delete()

            if (total <= maxSizeBytes) break
        }
    }

    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }
}