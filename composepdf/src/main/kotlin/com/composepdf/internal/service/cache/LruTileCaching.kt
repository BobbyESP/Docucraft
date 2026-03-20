package com.composepdf.internal.service.cache

import android.graphics.Bitmap
import android.util.LruCache

/**
 * An in-memory Least Recently Used (LRU) cache for high-resolution PDF page tiles.
 *
 * This implementation tracks capacity in **bytes** rather than the number of entries,
 * ensuring predictable memory usage based on the actual size of the cached [Bitmap]s.
 *
 * @param maxSizeBytes The maximum memory capacity in bytes before entries are evicted.
 * Defaults to 20% of the available heap.
 * @param onEvicted A callback invoked when a bitmap is removed from the cache,
 * allowing for manual resource cleanup (e.g., recycling).
 */
class LruTileCache(
    maxSizeBytes: Int = defaultSizeBytes(),
    private val onEvicted: (key: String, bitmap: Bitmap) -> Unit = { _, _ -> }
) : LruCache<String, Bitmap>(maxSizeBytes) {

    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount

    override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
        // Notify whenever a bitmap is displaced (evicted by LRU or replaced)
        if (oldValue !== newValue) {
            onEvicted(key, oldValue)
        }
    }

    companion object {
        fun defaultSizeBytes(): Int = (Runtime.getRuntime().maxMemory() * 0.20).toInt()
    }
}
