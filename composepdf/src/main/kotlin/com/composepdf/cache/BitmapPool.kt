package com.composepdf.cache

import android.graphics.Bitmap
import java.util.LinkedList

/**
 * A pool for recycling [Bitmap] objects to reduce memory allocations.
 * 
 * Bitmaps are expensive to allocate and can cause GC pressure when creating many
 * during scrolling. This pool maintains a collection of recycled bitmaps organized
 * by size buckets for efficient reuse.
 * 
 * When a bitmap is needed, the pool attempts to find a recycled bitmap of the same
 * size. If one is available, it's returned for reuse (after being erased). Otherwise,
 * a new bitmap is created.
 * 
 * @property maxPoolSize Maximum number of bitmaps to keep in the pool
 */
class BitmapPool(
    private val maxPoolSize: Int = DEFAULT_POOL_SIZE
) {
    
    private data class BitmapSpec(val width: Int, val height: Int, val config: Bitmap.Config)
    
    private val pool = LinkedHashMap<BitmapSpec, LinkedList<Bitmap>>()
    private var currentSize = 0
    
    /**
     * Gets a bitmap from the pool or creates a new one.
     * 
     * @param width The desired bitmap width
     * @param height The desired bitmap height
     * @param config The desired bitmap configuration
     * @return A bitmap of the specified dimensions (may be recycled or new)
     */
    @Synchronized
    fun get(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
        val spec = BitmapSpec(width, height, config)
        val bitmaps = pool[spec]
        
        return if (!bitmaps.isNullOrEmpty()) {
            currentSize--
            bitmaps.removeFirst().apply {
                // Clear the bitmap for reuse
                eraseColor(android.graphics.Color.TRANSPARENT)
            }
        } else {
            Bitmap.createBitmap(width, height, config)
        }
    }
    
    /**
     * Returns a bitmap to the pool for reuse.
     * 
     * Bitmaps that are already recycled or that would cause the pool to exceed
     * its maximum size are discarded.
     * 
     * @param bitmap The bitmap to return to the pool
     */
    @Synchronized
    fun put(bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        
        // If pool is full, recycle the bitmap instead of pooling
        if (currentSize >= maxPoolSize) {
            bitmap.recycle()
            return
        }
        
        val bitmapConfig = bitmap.config ?: Bitmap.Config.ARGB_8888
        val spec = BitmapSpec(bitmap.width, bitmap.height, bitmapConfig)
        val bitmaps = pool.getOrPut(spec) { LinkedList() }
        bitmaps.addLast(bitmap)
        currentSize++
    }
    
    /**
     * Clears all pooled bitmaps and releases their memory.
     */
    @Synchronized
    fun clear() {
        pool.values.forEach { bitmaps ->
            bitmaps.forEach { bitmap ->
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
        pool.clear()
        currentSize = 0
    }
    
    /**
     * Returns the current number of bitmaps in the pool.
     */
    @Synchronized
    fun size(): Int = currentSize
    
    companion object {
        private const val DEFAULT_POOL_SIZE = 10
    }
}
