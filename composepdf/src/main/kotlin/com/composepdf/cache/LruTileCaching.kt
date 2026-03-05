package com.composepdf.cache

import android.graphics.Bitmap
import android.util.LruCache

class LruTileCache(
    maxSizeKb: Int = defaultSizeKb(),
    private val onEvicted: (key: String) -> Unit = {}
) : LruCache<String, Bitmap>(maxSizeKb) {

    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024

    override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
        if (evicted) onEvicted(key)
    }

    companion object {
        fun defaultSizeKb(): Int = ((Runtime.getRuntime().maxMemory() / 1024L) / 3L).toInt()
    }
}