package com.composepdf.cache

import android.graphics.Bitmap
import android.util.Log
import java.util.ArrayDeque
import java.util.NavigableMap
import java.util.TreeMap
import androidx.core.graphics.createBitmap

private const val TAG = "BitmapPool"

/**
 * A memory pool for reusing [Bitmap] objects to reduce GC churn and memory fragmentation.
 *
 * This pool uses [Bitmap.reconfigure] to reuse existing bitmap allocations for new requests,
 * which is significantly faster than allocating new memory and prevents large heap spikes
 * during rapid scrolling or zooming.
 *
 * @property maxSizeBytes Maximum memory (in bytes) to hold in the pool. Default is 64 MB.
 *                        This allows holding at least one full-size 4096px square bitmap,
 *                        or several smaller ones.
 */
class BitmapPool(
    private val maxSizeBytes: Int = 64 * 1024 * 1024
) {
    // Map: ByteCount -> Stack of Bitmaps (LIFO for better cache locality)
    private val groupedBitmaps: NavigableMap<Int, ArrayDeque<Bitmap>> = TreeMap()
    private var currentSizeBytes = 0

    /**
     * Gets a mutable bitmap of the specified dimensions and config.
     * Tries to reuse an existing bitmap from the pool; creates a new one if none satisfy the requirements.
     */
    @Synchronized
    fun get(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
        // Calculate required size. For ARGB_8888, it's w * h * 4.
        val targetSizeBytes = width * height * 4

        // Find a bitmap that is at least as large as we need.
        // ceilingEntry returns the mapping with the least key greater than or equal to current.
        val entry = groupedBitmaps.ceilingEntry(targetSizeBytes)

        if (entry != null) {
            val size = entry.key
            val deque = entry.value

            val bitmap = deque.pollLast()

            // If this bucket is now empty, remove it to keep the map clean
            if (deque.isEmpty()) {
                groupedBitmaps.remove(size)
            }

            if (bitmap != null) {
                currentSizeBytes -= size
                try {
                    bitmap.reconfigure(width, height, config)
                    bitmap.eraseColor(0) // Clear old content
                    return bitmap
                } catch (e: IllegalArgumentException) {
                    Log.w(
                        TAG,
                        "Failed to reconfigure bitmap: ${e.message}. Recycling and creating new."
                    )
                    bitmap.recycle()
                    // Fallthrough to create new
                }
            }
        }

        // Fallback: create a fresh bitmap
        return createBitmap(width, height, config)
    }

    /**
     * Returns a bitmap to the pool for future reuse.
     * If the pool is full, the bitmap might be recycled immediately or older bitmaps evicted.
     */
    @Synchronized
    fun put(bitmap: Bitmap) {
        if (bitmap.isRecycled || !bitmap.isMutable) {
            // Cannot pool recycled or immutable bitmaps
            return
        }

        val size = bitmap.allocationByteCount

        // Clean up pool if adding this bitmap would exceed capacity.
        // We evict the smallest bitmaps first to try to accommodate larger ones (usually more scarce).
        while (currentSizeBytes + size > maxSizeBytes && groupedBitmaps.isNotEmpty()) {
            val smallestKey = groupedBitmaps.firstKey()
            val deque = groupedBitmaps[smallestKey]
            val evicted = deque?.pollFirst()

            if (evicted != null) {
                currentSizeBytes -= smallestKey
                // Do not call evicted.recycle() here to avoid Use-After-Free if something 
                // still holds a reference for a frame.
            }

            if (deque == null || deque.isEmpty()) {
                groupedBitmaps.remove(smallestKey)
            }
        }

        // Add to pool if there is space
        if (currentSizeBytes + size <= maxSizeBytes) {
            val deque = groupedBitmaps.getOrPut(size) { ArrayDeque() }
            deque.offerLast(bitmap)
            currentSizeBytes += size
        }
    }

    /**
     * Clears all bitmaps from the pool.
     */
    @Synchronized
    fun clear() {
        groupedBitmaps.clear()
        currentSizeBytes = 0
    }
}
