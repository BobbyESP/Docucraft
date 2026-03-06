package com.composepdf.cache

import android.graphics.Bitmap
import android.util.LruCache

/**
 * Composite key that uniquely identifies a rendered bitmap in [BitmapCache].
 */
data class PageCacheKey(
    val pageIndex: Int,
    val zoomLevel: Float,
    val width: Int,
    val height: Int
)

/**
 * In-memory LRU cache for rendered PDF page bitmaps.
 *
 * This cache notifies [onEvicted] when a bitmap is removed from its tracking.
 * It DOES NOT return bitmaps to the pool directly to avoid "trash" drawing,
 * as they might still be currently displayed on screen.
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
     * Removes all cached bitmaps that belong to [pageIndex].
     *
     * Useful when a page is no longer needed (e.g. after a document close) and its
     * memory should be reclaimed before the LRU eviction cycle runs naturally.
     */
    @Suppress("unused")
    fun clearPage(pageIndex: Int) {
        cache.snapshot().keys
            .filter { it.pageIndex == pageIndex }
            .forEach { cache.remove(it) }
    }

    companion object {
        fun calculateDefaultCacheSize(): Int {
            val maxMemory = Runtime.getRuntime().maxMemory()
            // Reduced to 15% of the available heap for the bitmap cache (base pages).
            // Combined with tile cache (20%) and pool, this leaves more breathing room for the GC.
            return (maxMemory * 0.15).toInt()
        }
    }
}
