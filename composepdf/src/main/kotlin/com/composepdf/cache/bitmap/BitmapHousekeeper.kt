package com.composepdf.cache.bitmap

import android.graphics.Bitmap
import com.composepdf.state.PdfViewerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages the bitmap eviction policy and handles the delayed return of bitmaps to the pool.
 *
 * This class ensures that bitmaps are kept alive for a short grace period after being evicted from the
 * cache. This prevents issues where Compose might still attempt to draw a bitmap for a few frames after
 * it has been removed from the active cache maps.
 */
internal class BitmapHousekeeper(
    private val scope: CoroutineScope,
    private val state: PdfViewerState,
    private val renderedPagesProvider: () -> Map<Int, Bitmap>,
    private val bitmapPool: BitmapPool
) {
    val bitmapCache = BitmapCache { evictedBitmap ->
        scope.launch {
            delay(BITMAP_RETURN_DELAY_MS)
            withContext(Dispatchers.Main.immediate) {
                if (evictedBitmap.isRecycled) return@withContext
                val stillUsedByPages = renderedPagesProvider().values.any { it === evictedBitmap }
                val stillUsedByTiles = state.getAllTiles().values.any { it === evictedBitmap }
                if (!stillUsedByPages && !stillUsedByTiles) {
                    bitmapPool.put(evictedBitmap)
                }
            }
        }
    }

    fun clear() {
        bitmapCache.clear()
    }

    private companion object {
        const val BITMAP_RETURN_DELAY_MS = 400L
    }
}
