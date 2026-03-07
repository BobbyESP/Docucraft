package com.composepdf.cache

import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.createBitmap
import java.util.ArrayDeque
import java.util.NavigableMap
import java.util.TreeMap

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
    private val maxSizeBytes: Int = DEFAULT_POOL_SIZE_BYTES
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
        // Calculate required size based on the requested bitmap configuration.
        val bytesPerPixel = when (config) {
            Bitmap.Config.ALPHA_8 -> 1

            Bitmap.Config.RGB_565,
            Bitmap.Config.ARGB_4444 -> 2

            Bitmap.Config.ARGB_8888 -> 4
            else -> throw UnsupportedOperationException("Unsupported config: $config")
        }
        val targetSizeBytesLong =
            width.toLong() * height.toLong() * bytesPerPixel.toLong()
        if (targetSizeBytesLong > Int.MAX_VALUE) {
            Log.w(
                TAG,
                "Requested bitmap is too large for pooling: " +
                        "width=$width, height=$height, config=$config"
            )
            // Avoid integer overflow and pooling for extremely large bitmaps.
            return createBitmap(width, height, config)
        }
        val targetSizeBytes = targetSizeBytesLong.toInt()

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
                if (bitmap.isRecycled) {
                    // Already recycled — skip and fall through to create new.
                } else {
                    try {
                        bitmap.reconfigure(width, height, config)
                        bitmap.eraseColor(0)
                        currentSizeBytes -= size
                        return bitmap
                    } catch (e: IllegalArgumentException) {
                        Log.w(
                            TAG,
                            "Failed to reconfigure bitmap: ${e.message}. Recycling and creating new."
                        )
                        bitmap.recycle()
                    }
                }
            }
        }

        // Fallback: create a fresh bitmap
        return createBitmap(width, height, config)
    }

    /**
     * Returns a bitmap to the pool for future reuse.
     *
     * If the pool is full, old entries are dropped (but **not** recycled — they may still be
     * referenced by a Compose draw scope for one or two frames). The GC will collect them
     * once all references are gone. Only bitmaps that cannot fit at all are recycled
     * immediately, because they were never handed out and thus have no external references.
     */
    @Synchronized
    fun put(bitmap: Bitmap) {
        if (bitmap.isRecycled || !bitmap.isMutable) {
            return
        }

        val size = bitmap.allocationByteCount

        // Clean up pool if adding this bitmap would exceed capacity.
        while (currentSizeBytes + size > maxSizeBytes && groupedBitmaps.isNotEmpty()) {
            val smallestKey = groupedBitmaps.firstKey()
            val deque = groupedBitmaps[smallestKey]
            val evicted = deque?.pollFirst()

            if (evicted != null) {
                currentSizeBytes -= smallestKey
                // Do NOT recycle here — the bitmap may still be drawn by Compose.
                // Let the GC collect it once all references are released.
            }

            if (deque == null || deque.isEmpty()) {
                groupedBitmaps.remove(smallestKey)
            }
        }

        // Add to pool if there is space.
        if (currentSizeBytes + size <= maxSizeBytes) {
            val deque = groupedBitmaps.getOrPut(size) { ArrayDeque() }
            deque.offerLast(bitmap)
            currentSizeBytes += size
        }
        // If it doesn't fit even after eviction, just let GC handle it.
    }

    /**
     * Clears all bitmaps from the pool.
     *
     * Bitmaps are NOT recycled because they may still be referenced by in-flight Compose
     * draw scopes. The GC will collect them once all references are released.
     */
    @Synchronized
    fun clear() {
        groupedBitmaps.clear()
        currentSizeBytes = 0
    }

    companion object {
        /**
         * 32 MB default pool — enough for ~8 full-size 2048x2048 ARGB_8888 tiles or ~128 small
         * 256x256 tiles. Keep this well under a third of the typical 256 MB app heap to leave
         * headroom for the LRU caches and the GC.
         */
        const val DEFAULT_POOL_SIZE_BYTES = 32 * 1024 * 1024
    }
}
