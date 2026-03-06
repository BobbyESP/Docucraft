package com.composepdf.cache

import android.graphics.Bitmap
import android.util.LruCache

/**
 * In-memory LRU cache for high-resolution PDF tiles.
 *
 * Measures capacity in **bytes** (same unit as [BitmapCache]) so both caches
 * can be reasoned about with a single mental model.
 *
 * The [onEvicted] callback is called **only** when the LRU algorithm removes an
 * entry automatically (i.e. `evicted == true`). Manual `remove()` calls do NOT
 * trigger the callback, which prevents double-eviction when tiles are pruned
 * manually before the LRU would remove them automatically.
 *
 * @param maxSizeBytes Maximum memory in bytes. Defaults to 1/3 of the app heap.
 * @param onEvicted Invoked with the tile key when an entry is auto-evicted by LRU pressure.
 */
class LruTileCache(
    maxSizeBytes: Int = defaultSizeBytes(),
    private val onEvicted: (key: String) -> Unit = {}
) : LruCache<String, Bitmap>(maxSizeBytes) {

    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount

    override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
        // Only notify on automatic LRU eviction, not on explicit remove() calls.
        if (evicted) onEvicted(key)
    }

    companion object {
        // Reduced to 20% of the app's max heap for the on-memory tile caching.
        fun defaultSizeBytes(): Int = (Runtime.getRuntime().maxMemory() * 0.20).toInt()
    }
}