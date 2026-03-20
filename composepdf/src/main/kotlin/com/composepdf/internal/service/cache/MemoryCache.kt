package com.composepdf.internal.service.cache

import android.graphics.Bitmap
import android.util.LruCache

/**
 * A generic in-memory LRU cache for bitmaps.
 *
 * Replaces both BitmapCache and LruTileCaching with a single implementation.
 * Tracks size in bytes.
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

