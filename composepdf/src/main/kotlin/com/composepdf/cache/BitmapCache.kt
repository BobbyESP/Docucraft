package com.composepdf.cache

import android.graphics.Bitmap
import android.util.LruCache

/**
 * An in-memory Least Recently Used (LRU) cache for rendered PDF page bitmaps.
 *
 * This cache tracks memory usage based on the [Bitmap.allocationByteCount] of each entry
 * to stay within a specified [maxSizeBytes] limit. It provides an [onEvicted] callback
 * to handle resource cleanup (such as bitmap recycling) when items are removed.
 *
 * Use [clearPagesOutside] to proactively manage memory by evicting pages that are
 * no longer within a visible or buffered range.
 *
 * @param maxSizeBytes The maximum cumulative size in bytes the cache should hold.
 * Defaults to 15% of the available runtime memory.
 * @param onEvicted A callback triggered whenever a bitmap is removed from the cache,
 * providing an opportunity to recycle the bitmap or release resources.
 */
class BitmapCache(
    maxSizeBytes: Int = calculateDefaultCacheSize(),
    private val onEvicted: (Bitmap) -> Unit = {}
) {

    private val cache = object : LruCache<PageCacheKey, Bitmap>(maxSizeBytes) {
        override fun sizeOf(key: PageCacheKey, value: Bitmap) = value.allocationByteCount

        override fun entryRemoved(
            evicted: Boolean,
            key: PageCacheKey,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            if (oldValue !== newValue) {
                onEvicted(oldValue)
            }
        }
    }

    fun get(key: PageCacheKey): Bitmap? = cache.get(key)?.takeIf { !it.isRecycled }

    fun put(key: PageCacheKey, bitmap: Bitmap) {
        if (!bitmap.isRecycled) cache.put(key, bitmap)
    }

    fun remove(key: PageCacheKey): Bitmap? = cache.remove(key)

    fun clear() = cache.evictAll()

    /**
     * Proactively removes cached bitmaps for pages outside the specified range.
     * This is essential for maintaining a constant memory footprint during large jumps.
     */
    fun clearPagesOutside(keepRange: IntRange) {
        val snapshot = cache.snapshot()
        for (key in snapshot.keys) {
            if (key.pageIndex !in keepRange) {
                cache.remove(key)
            }
        }
    }

    companion object {
        fun calculateDefaultCacheSize(): Int {
            val maxMemory = Runtime.getRuntime().maxMemory()
            return (maxMemory * 0.15).toInt()
        }
    }
}
