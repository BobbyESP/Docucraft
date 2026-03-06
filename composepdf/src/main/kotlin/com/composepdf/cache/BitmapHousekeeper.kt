package com.composepdf.cache

import android.graphics.Bitmap
import com.composepdf.state.PdfViewerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Encapsulates bitmap eviction policy and delayed return-to-pool housekeeping.
 *
 * The viewer keeps rendered bitmaps alive for a short grace period because Compose may still draw
 * them for one or two frames after they leave the scheduler/cache maps. This class centralizes
 * that policy so the controller does not own bitmap lifecycle details.
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
