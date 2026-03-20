package com.composepdf.internal.service.cache

import android.graphics.Bitmap
import android.util.LruCache

/**
 * A generic in-memory LRU (Least Recently Used) cache specifically designed for storing [Bitmap] objects.
 *
 * This implementation tracks the size of the cache in bytes based on the [Bitmap.allocationByteCount]
 * of its entries, ensuring that memory usage stays within the specified limit.
 *
 * @param K The type of key used to identify cached bitmaps.
 * @param maxSizeBytes The maximum total size in bytes that this cache should occupy.
 * @param onEvicted An optional callback invoked when a bitmap is evicted or removed from the cache.
 * Useful for recycling bitmaps or releasing associated resources.
 */
class MemoryCache<K : Any>(
    maxSizeBytes: Int,
    private val onEvicted: (key: K, bitmap: Bitmap) -> Unit = { _, _ -> }
) : LruCache<K, Bitmap>(maxSizeBytes) {

    override fun sizeOf(key: K, value: Bitmap): Int = value.allocationByteCount

    override fun entryRemoved(evicted: Boolean, key: K, oldValue: Bitmap, newValue: Bitmap?) {
        if (oldValue !== newValue) {
            onEvicted(key, oldValue)
        }
    }

    fun clear() = evictAll()
}

