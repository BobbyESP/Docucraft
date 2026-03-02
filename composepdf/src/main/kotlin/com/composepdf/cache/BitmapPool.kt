package com.composepdf.cache

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap

/**
 * Allocates bitmaps for PDF page rendering.
 *
 * ## Why no recycling?
 *
 * The previous implementation recycled bitmaps via an LruCache eviction callback.
 * That caused `Canvas: trying to use a recycled bitmap` crashes because Compose
 * may still be drawing a bitmap on the GPU after the LruCache evicts it and the
 * pool calls `bitmap.recycle()`.
 *
 * The safe approach: **never call `bitmap.recycle()` manually**. Bitmaps handed
 * to Compose become referenced by `ImageBitmap` / `BitmapPainter` and will be
 * GC'd normally once no longer referenced. The GC + Android's bitmap allocator
 * handles memory far better than manual pooling with Compose.
 *
 * This class is kept as a thin factory so call-sites don't need to change.
 */
class BitmapPool {

    /**
     * Allocates a new [Bitmap] of the given dimensions and config.
     * Always returns a fresh bitmap — no pooling is performed.
     */
    fun get(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
        return createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), config)
    }

    /**
     * No-op. Kept for API compatibility; bitmaps are not manually recycled.
     */
    @Suppress("UNUSED_PARAMETER")
    fun put(bitmap: Bitmap) = Unit

    /**
     * No-op. Kept for API compatibility.
     */
    fun clear() = Unit
}
