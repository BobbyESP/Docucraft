package com.composepdf.cache

import android.graphics.Bitmap
import androidx.collection.LruCache

object LruTileCaching: LruCache<String, Bitmap>(
    // Use ~25% of the app's max heap size (in KB) for tile memory.
    ((Runtime.getRuntime().maxMemory() / 1024L) / 4L).toInt()
) {
    override fun sizeOf(key: String, value: Bitmap): Int {
        // Measure each bitmap in kilobytes so the cache size reflects actual memory usage.
        return value.allocationByteCount / 1024
    }
}