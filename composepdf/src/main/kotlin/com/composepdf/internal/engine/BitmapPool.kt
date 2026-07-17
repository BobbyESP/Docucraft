/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.engine

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import java.util.ArrayDeque
import java.util.TreeMap

/**
 * A thread-safe pool of reusable [Bitmap]s.
 *
 * Bitmaps are never recycled: when the pool is full or an entry is unusable it is simply dropped
 * for the garbage collector, which makes it impossible for Compose to ever draw a recycled bitmap.
 * Reuse works via [Bitmap.reconfigure] on a best-fit (ceiling by byte count) entry, which is ideal
 * for the engine's workload where almost every request is a fixed-size tile.
 *
 * Operations are short critical sections guarded by a plain lock, so the pool can be used from any
 * thread without suspension.
 */
class BitmapPool(private val maxSizeBytes: Int = DEFAULT_POOL_SIZE_BYTES) {
    private val lock = Any()
    private val buckets = TreeMap<Int, ArrayDeque<Bitmap>>()
    private var currentBytes = 0L

    fun get(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
        val required = requiredBytes(width, height, config)
        if (required <= 0) return createBitmap(width, height, config)

        synchronized(lock) {
            var entry = buckets.ceilingEntry(required)
            while (entry != null) {
                val size = entry.key
                val deque = entry.value
                val candidate = deque.pollLast()
                if (deque.isEmpty()) buckets.remove(size)
                if (candidate != null) {
                    currentBytes -= size
                    if (!candidate.isRecycled && candidate.isMutable) {
                        try {
                            candidate.reconfigure(width, height, config)
                            candidate.eraseColor(0)
                            return candidate
                        } catch (_: IllegalArgumentException) {
                            // Cannot host these dimensions; drop it and keep searching.
                        }
                    }
                }
                entry = buckets.ceilingEntry(size + 1)
            }
        }
        return createBitmap(width, height, config)
    }

    /** Offers [bitmap] back for reuse. Never recycles; drops silently when full or unusable. */
    fun put(bitmap: Bitmap) {
        if (bitmap.isRecycled || !bitmap.isMutable) return
        val size = bitmap.allocationByteCount
        if (size > maxSizeBytes) return

        synchronized(lock) {
            while (currentBytes + size > maxSizeBytes && buckets.isNotEmpty()) {
                val smallest = buckets.firstEntry()
                val evicted = smallest.value.pollFirst()
                if (evicted != null) currentBytes -= smallest.key
                if (smallest.value.isEmpty()) buckets.remove(smallest.key)
            }
            if (currentBytes + size <= maxSizeBytes) {
                buckets.getOrPut(size) { ArrayDeque() }.offerLast(bitmap)
                currentBytes += size
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            buckets.clear()
            currentBytes = 0
        }
    }

    private fun requiredBytes(width: Int, height: Int, config: Bitmap.Config): Int {
        val bytesPerPixel =
            when (config) {
                Bitmap.Config.ALPHA_8 -> 1
                Bitmap.Config.RGB_565,
                Bitmap.Config.ARGB_4444 -> 2
                else -> 4
            }
        val total = width.toLong() * height.toLong() * bytesPerPixel
        return if (total in 1..Int.MAX_VALUE.toLong()) total.toInt() else -1
    }

    companion object {
        const val DEFAULT_POOL_SIZE_BYTES = 32 * 1024 * 1024
    }
}
