package com.composepdf.cache

import android.graphics.Bitmap
import android.util.LruCache
import com.composepdf.cache.BitmapCache.Companion.calculateDefaultCacheSize

/**
 * Composite key that uniquely identifies a rendered bitmap in [BitmapCache].
 *
 * All four fields are required for correct cache invalidation:
 * - [pageIndex]  — different pages must never share a bitmap.
 * - [zoomLevel]  — rounded to 2 decimal places by [RenderScheduler] to avoid float-drift
 *                  misses between frames rendered at the same logical zoom step.
 * - [width]/[height] — the render dimensions in pixels. Two identical zoom levels
 *                      on different viewport widths (e.g. split-screen resize) must
 *                      produce separate cache entries.
 */
data class PageCacheKey(
    val pageIndex: Int,
    val zoomLevel: Float,
    val width: Int,
    val height: Int
)

/**
 * In-memory LRU cache for rendered PDF page bitmaps, keyed by [PageCacheKey].
 *
 * ## Eviction strategy
 *
 * Uses [LruCache] with byte-count sizing (via [Bitmap.allocationByteCount]).
 * The default maximum is ~20 % of the JVM heap to leave room for the UI and other
 * components. Entries are evicted in least-recently-used order when the limit is hit.
 *
 * ## Why bitmaps are never recycled on eviction
 *
 * Calling [Bitmap.recycle] in `entryRemoved` caused `Canvas: trying to use a recycled
 * bitmap` crashes. When [LruCache] evicts a bitmap, Compose may still hold a reference
 * to it via [androidx.compose.ui.graphics.ImageBitmap] and draw it on the GPU render thread.
 * Recycling it at that moment invalidates the underlying pixel buffer mid-draw.
 *
 * The safe approach: drop the reference and let the GC collect it once Compose has
 * released its own reference. Android's bitmap allocator recycles the native memory
 * automatically at that point.
 *
 * @param maxSizeBytes LRU capacity in bytes. Defaults to [calculateDefaultCacheSize].
 */
class BitmapCache(
    maxSizeBytes: Int = calculateDefaultCacheSize(),
    @Suppress("UNUSED_PARAMETER") bitmapPool: BitmapPool? = null
) {

    private val cache = object : LruCache<PageCacheKey, Bitmap>(maxSizeBytes) {
        override fun sizeOf(key: PageCacheKey, value: Bitmap) = value.allocationByteCount

        // entryRemoved intentionally does NOT recycle — let GC handle it.
    }

    /**
     * Retrieves a cached bitmap for the given key.
     *
     * @param key The cache key
     * @return The cached bitmap, or null if not found
     */
    fun get(key: PageCacheKey): Bitmap? = cache.get(key)?.takeIf { !it.isRecycled }

    /**
     * Stores a bitmap in the cache.
     *
     * @param key The cache key
     * @param bitmap The bitmap to cache
     */
    fun put(key: PageCacheKey, bitmap: Bitmap) {
        if (!bitmap.isRecycled) cache.put(key, bitmap)
    }

    /**
     * Removes a specific entry from the cache.
     *
     * @param key The cache key to remove
     * @return The removed bitmap, or null if not found
     */
    fun remove(key: PageCacheKey): Bitmap? = cache.remove(key)

    /**
     * Removes all entries for a specific page index, regardless of zoom level.
     *
     * @param pageIndex The page index to clear
     */
    fun clearPage(pageIndex: Int) {
        cache.snapshot().keys
            .filter { it.pageIndex == pageIndex }
            .forEach { cache.remove(it) }
    }

    /**
     * Clears all cached bitmaps.
     */
    fun clear() = cache.evictAll()

    /**
     * Returns the current size of the cache in bytes.
     */
    fun size(): Int = cache.size()

    companion object {
        /**
         * Calculates a reasonable default cache size based on available memory.
         * Uses approximately 25% of the available heap.
         */
        fun calculateDefaultCacheSize(): Int {
            val maxMemory = Runtime.getRuntime().maxMemory()
            return (maxMemory / 5).toInt()   // 20 % of heap
        }
    }
}
