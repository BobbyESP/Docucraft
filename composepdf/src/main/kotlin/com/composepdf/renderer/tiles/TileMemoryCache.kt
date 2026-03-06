package com.composepdf.renderer.tiles

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.composepdf.cache.LruTileCache

/**
 * In-memory cache for rendered tiles.
 *
 * Maintains both:
 *
 * - Bitmap storage
 * - ImageBitmap snapshot for Compose rendering
 */
class TileMemoryCache(
    maxSize: Int
) {
    private val lru = LruTileCache(maxSize)

    private val bitmapSnapshot = mutableMapOf<String, ImageBitmap>()

    fun get(key: String): Bitmap? = lru[key]

    fun put(key: String, bitmap: Bitmap) {
        lru.put(key, bitmap)
        bitmapSnapshot[key] = bitmap.asImageBitmap()
    }

    fun snapshot(): Map<String, ImageBitmap> =
        bitmapSnapshot
}