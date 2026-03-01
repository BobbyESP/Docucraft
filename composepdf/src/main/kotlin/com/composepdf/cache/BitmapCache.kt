package com.composepdf.cache

import android.graphics.Bitmap
import android.util.LruCache

/**
 * Key for caching rendered PDF page bitmaps.
 * 
 * The key includes page index, zoom level, and render dimensions to ensure
 * bitmaps are correctly invalidated when the render parameters change.
 * 
 * @property pageIndex The zero-based page index
 * @property zoomLevel The zoom level at which the page was rendered
 * @property width The rendered bitmap width in pixels
 * @property height The rendered bitmap height in pixels
 */
data class PageCacheKey(
    val pageIndex: Int,
    val zoomLevel: Float,
    val width: Int,
    val height: Int
)

/**
 * LRU cache for rendered PDF page bitmaps.
 * 
 * This cache automatically evicts least recently used bitmaps when the memory limit is reached.
 * Evicted bitmaps are returned to a [BitmapPool] for reuse, minimizing memory allocations.
 * 
 * The cache size is calculated based on available heap memory, typically using 25% of the
 * maximum heap size to leave room for other application memory needs.
 * 
 * @property maxSizeBytes Maximum cache size in bytes
 * @property bitmapPool Optional pool for recycling evicted bitmaps
 */
class BitmapCache(
    maxSizeBytes: Int = calculateDefaultCacheSize(),
    private val bitmapPool: BitmapPool? = null
) {
    
    private val cache: LruCache<PageCacheKey, Bitmap> = object : LruCache<PageCacheKey, Bitmap>(maxSizeBytes) {
        override fun sizeOf(key: PageCacheKey, value: Bitmap): Int {
            return value.allocationByteCount
        }
        
        override fun entryRemoved(
            evicted: Boolean,
            key: PageCacheKey,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            if (evicted && newValue == null) {
                // Bitmap was evicted, return to pool for reuse
                bitmapPool?.put(oldValue)
            }
        }
    }
    
    /**
     * Retrieves a cached bitmap for the given key.
     * 
     * @param key The cache key
     * @return The cached bitmap, or null if not found
     */
    fun get(key: PageCacheKey): Bitmap? = cache.get(key)
    
    /**
     * Stores a bitmap in the cache.
     * 
     * @param key The cache key
     * @param bitmap The bitmap to cache
     */
    fun put(key: PageCacheKey, bitmap: Bitmap) {
        cache.put(key, bitmap)
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
    fun clear() {
        cache.evictAll()
    }
    
    /**
     * Returns the current size of the cache in bytes.
     */
    fun size(): Int = cache.size()
    
    /**
     * Returns the maximum size of the cache in bytes.
     */
    fun maxSize(): Int = cache.maxSize()
    
    companion object {
        /**
         * Calculates a reasonable default cache size based on available memory.
         * Uses approximately 25% of the available heap.
         */
        fun calculateDefaultCacheSize(): Int {
            val maxMemory = Runtime.getRuntime().maxMemory()
            return (maxMemory / 4).toInt()
        }
    }
}
