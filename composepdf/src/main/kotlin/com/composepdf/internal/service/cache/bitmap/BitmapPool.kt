package com.composepdf.internal.service.cache.bitmap

import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.ArrayDeque
import java.util.NavigableMap
import java.util.TreeMap

private const val TAG = "BitmapPool"

/**
 * Data class to track the current state of the pool for telemetry/monitoring.
 */
data class BitmapPoolStats(
    val currentBytes: Long = 0,
    val maxBytes: Long = 0,
    val bitmapCount: Int = 0,
    val reuseCount: Long = 0,
    val creationCount: Long = 0
)

/**
 * A coroutine-safe memory pool for [Bitmap] objects designed for high-performance reuse.
 *
 * This pool facilitates efficient bitmap management by:
 * 1. **Coroutine Integration**: Using a non-blocking [Mutex] for synchronization, making it
 *    suitable for asynchronous rendering pipelines.
 * 2. **Compose Compatibility**: Avoiding explicit [Bitmap.recycle] calls to prevent
 *    "use-after-recycled" errors during Jetpack Compose's asynchronous drawing cycles.
 * 3. **Smart Re-allocation**: Utilizing a [NavigableMap] to find the best-fit bitmap
 *    (ceiling match) and [Bitmap.reconfigure] to minimize memory churn.
 * 4. **Observability**: Providing a [StateFlow] of [BitmapPoolStats] for real-time
 *    telemetry and monitoring of cache hits, misses, and memory usage.
 *
 * @param maxSizeBytes The maximum cumulative size of bitmaps allowed in the pool before eviction.
 */
class BitmapPool(
    private val maxSizeBytes: Int = DEFAULT_POOL_SIZE_BYTES
) {
    private val mutex = Mutex()

    // Map: ByteCount -> Stack of Bitmaps (LIFO for better cache locality)
    private val groupedBitmaps: NavigableMap<Int, ArrayDeque<Bitmap>> = TreeMap()

    private val _stats = MutableStateFlow(BitmapPoolStats(maxBytes = maxSizeBytes.toLong()))
    val stats: StateFlow<BitmapPoolStats> = _stats.asStateFlow()

    /**
     * Gets a mutable bitmap of the specified dimensions and config.
     * Tries to reuse an existing bitmap from the pool; creates a new one if none satisfy the requirements.
     *
     * This is a suspend function to ensure it plays well with the rendering pipeline's coroutines.
     */
    suspend fun get(
        width: Int,
        height: Int,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Bitmap {
        val targetSizeBytes = calculateRequiredBytes(width, height, config)

        if (targetSizeBytes == -1) {
            return createFreshBitmap(width, height, config)
        }

        mutex.withLock {
            // Find a bitmap that is at least as large as we need.
            var entry = groupedBitmaps.ceilingEntry(targetSizeBytes)

            while (entry != null) {
                val size = entry.key
                val deque = entry.value
                val bitmap = deque.pollLast()

                if (deque.isEmpty()) {
                    groupedBitmaps.remove(size)
                }

                if (bitmap != null) {
                    if (!bitmap.isRecycled && bitmap.isMutable) {
                        return try {
                            bitmap.reconfigure(width, height, config)
                            bitmap.eraseColor(0)

                            _stats.update {
                                it.copy(
                                    currentBytes = it.currentBytes - size,
                                    bitmapCount = it.bitmapCount - 1,
                                    reuseCount = it.reuseCount + 1
                                )
                            }

                            bitmap
                        } catch (e: IllegalArgumentException) {
                            Log.w(TAG, "Failed to reconfigure bitmap, searching next bucket...")
                            // Continue searching in other buckets if reconfigure fails
                            entry = groupedBitmaps.ceilingEntry(size + 1)
                            continue
                        }
                    } else {
                        // Bitmap was externally recycled or not mutable, update stats and continue
                        _stats.update {
                            it.copy(
                                currentBytes = it.currentBytes - size,
                                bitmapCount = it.bitmapCount - 1
                            )
                        }
                        entry = groupedBitmaps.ceilingEntry(size + 1)
                        continue
                    }
                }
                entry = groupedBitmaps.ceilingEntry(size + 1)
            }
        }

        return createFreshBitmap(width, height, config)
    }

    /**
     * Returns a bitmap to the pool for future reuse.
     *
     * Important: This implementation NEVER calls [Bitmap.recycle]. If the pool is full,
     * bitmaps are simply dropped and left for the Garbage Collector.
     */
    suspend fun put(bitmap: Bitmap) {
        if (bitmap.isRecycled || !bitmap.isMutable) return

        val size = bitmap.allocationByteCount
        if (size > maxSizeBytes) return

        mutex.withLock {
            // Evict until we have space
            while (_stats.value.currentBytes + size > maxSizeBytes && groupedBitmaps.isNotEmpty()) {
                val smallestKey = groupedBitmaps.firstKey()
                val deque = groupedBitmaps[smallestKey]
                val evicted = deque?.pollFirst()

                if (evicted != null) {
                    _stats.update {
                        it.copy(
                            currentBytes = it.currentBytes - smallestKey,
                            bitmapCount = it.bitmapCount - 1
                        )
                    }
                }

                if (deque.isNullOrEmpty()) {
                    groupedBitmaps.remove(smallestKey)
                }
            }

            // Add if it fits
            if (_stats.value.currentBytes + size <= maxSizeBytes) {
                val deque = groupedBitmaps.getOrPut(size) { ArrayDeque() }
                deque.offerLast(bitmap)
                _stats.update {
                    it.copy(
                        currentBytes = it.currentBytes + size,
                        bitmapCount = it.bitmapCount + 1
                    )
                }
            }
        }
    }

    suspend fun clear() {
        mutex.withLock {
            groupedBitmaps.clear()
            _stats.update { it.copy(currentBytes = 0, bitmapCount = 0) }
        }
    }

    private fun createFreshBitmap(w: Int, h: Int, config: Bitmap.Config): Bitmap {
        _stats.update { it.copy(creationCount = it.creationCount + 1) }
        return createBitmap(w, h, config)
    }

    private fun calculateRequiredBytes(width: Int, height: Int, config: Bitmap.Config): Int {
        val bytesPerPixel = when (config) {
            Bitmap.Config.ALPHA_8 -> 1
            Bitmap.Config.RGB_565, Bitmap.Config.ARGB_4444 -> 2
            Bitmap.Config.ARGB_8888 -> 4
            else -> 4 // Safe default
        }
        val total = width.toLong() * height.toLong() * bytesPerPixel
        return if (total > Int.MAX_VALUE) -1 else total.toInt()
    }

    companion object {
        const val DEFAULT_POOL_SIZE_BYTES = 32 * 1024 * 1024
    }
}
