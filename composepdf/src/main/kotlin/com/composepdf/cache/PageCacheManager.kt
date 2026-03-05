package com.composepdf.cache

import android.graphics.Bitmap

class PageCacheManager(
    private val memory: BitmapCache,
    private val disk: PageDiskCache
) {
    fun get(key: PageCacheKey): Bitmap? {
        // Memory
        memory.get(key)?.let {
            return it
        }

        // Disk
        disk.get(key)?.let {
            memory.put(key, it)
            return it
        }

        // Miss
        return null
    }

    suspend fun put(key: PageCacheKey, bitmap: Bitmap) {
        memory.put(key, bitmap)
        disk.put(key, bitmap)
    }

    fun clear() {
        memory.clear()
        disk.clear()
    }
}