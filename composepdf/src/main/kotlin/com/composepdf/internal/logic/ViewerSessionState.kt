package com.composepdf.internal.logic

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.composepdf.RemotePdfState
import com.composepdf.RenderTelemetrySnapshot
import com.composepdf.internal.logic.tiles.TileKey
import com.composepdf.internal.service.cache.MemoryCache
import com.composepdf.internal.service.cache.bitmap.BitmapPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Immutable UI-ready representation of a published high-resolution tile.
 */
internal data class PublishedTile(
    val cacheKey: String,
    val tileKey: TileKey,
    val imageBitmap: ImageBitmap
)

/**
 * Internal document-session store for the viewer.
 *
 * This version enforces strict bitmap ownership and handles asynchronous pool returns.
 * When a tile is evicted from the [tileCache], it is returned to the [BitmapPool]
 * using the provided [scope].
 */
internal class ViewerSessionState(
    private val scope: CoroutineScope,
    private val bitmapPool: BitmapPool
) {
    var pageCount: Int by mutableIntStateOf(0)
    var isLoading: Boolean by mutableStateOf(true)
    var error: Throwable? by mutableStateOf(null)
    var remoteState: RemotePdfState by mutableStateOf(RemotePdfState.Idle)
    val isLoaded: Boolean get() = !isLoading && error == null && pageCount > 0

    private val tileCache = MemoryCache<String>(
        maxSizeBytes = (Runtime.getRuntime().maxMemory() * 0.20).toInt(),
        onEvicted = { key, bitmap -> handleTileEviction(key, bitmap) }
    )
    
    private val _telemetrySnapshot = MutableStateFlow(RenderTelemetrySnapshot())
    val telemetrySnapshot: StateFlow<RenderTelemetrySnapshot> = _telemetrySnapshot.asStateFlow()

    fun updateTelemetry(snapshot: RenderTelemetrySnapshot) {
        _telemetrySnapshot.value = snapshot
    }

    var tileRevision by mutableIntStateOf(0)
        private set

    private var tilesSnapshot: Map<String, Bitmap> = emptyMap()
    private var publishedTilesByPage: Map<Int, List<PublishedTile>> = emptyMap()

    fun beginDocumentLoad() {
        pageCount = 0
        isLoading = true
        error = null
        remoteState = RemotePdfState.Idle
    }

    fun updateRemoteState(state: RemotePdfState) {
        remoteState = state
    }

    fun completeDocumentLoad(pageCount: Int) {
        this.pageCount = pageCount
        isLoading = false
        error = null
    }

    fun failDocumentLoad(error: Throwable) {
        this.error = error
        isLoading = false
    }

    fun getTile(key: String): Bitmap? = tileCache[key]

    fun getAllTiles(): Map<String, Bitmap> = tilesSnapshot

    fun getImageBitmapTilesForPage(pageIndex: Int): List<PublishedTile> =
        publishedTilesByPage[pageIndex].orEmpty()

    suspend fun putTile(key: String, bitmap: Bitmap) = withContext(Dispatchers.Main.immediate) {
        val tileKey = TileKey.fromCacheKey(key) ?: return@withContext
        tileCache.put(key, bitmap)

        val publishedTile = PublishedTile(
            cacheKey = key,
            tileKey = tileKey,
            imageBitmap = bitmap.asImageBitmap()
        )

        tilesSnapshot = tilesSnapshot + (key to bitmap)

        val updatedPageTiles = publishedTilesByPage.toMutableMap()
        val pageTiles = updatedPageTiles[tileKey.pageIndex].orEmpty()
            .filterNot { it.cacheKey == key }
            .toMutableList()
        pageTiles += publishedTile
        updatedPageTiles[tileKey.pageIndex] = pageTiles.sortedBy { it.cacheKey }
        publishedTilesByPage = updatedPageTiles
        tileRevision++
    }

    suspend fun pruneTiles(predicate: (String) -> Boolean) =
        withContext(Dispatchers.Main.immediate) {
            val keysToRemove = tilesSnapshot.keys.filterNot(predicate)
            if (keysToRemove.isEmpty()) return@withContext

            keysToRemove.forEach { key ->
                tileCache.remove(key) // Triggers handleTileEviction
            }
            tileRevision++
        }

    suspend fun clearTiles() = withContext(Dispatchers.Main.immediate) {
        tileCache.evictAll() // Triggers handleTileEviction for all
        tileRevision++
    }

    private fun handleTileEviction(key: String, bitmap: Bitmap) {
        scope.launch {
            // Snapshot cleanup on Main Thread for UI consistency
            withContext(Dispatchers.Main.immediate) {
                if (tilesSnapshot.containsKey(key)) {
                    tilesSnapshot = tilesSnapshot - key
                    TileKey.fromCacheKey(key)?.let { tileKey ->
                        val updatedByPage = publishedTilesByPage.toMutableMap()
                        val remaining = updatedByPage[tileKey.pageIndex].orEmpty()
                            .filterNot { it.cacheKey == key }
                        if (remaining.isEmpty()) updatedByPage.remove(tileKey.pageIndex)
                        else updatedByPage[tileKey.pageIndex] = remaining
                        publishedTilesByPage = updatedByPage
                    }
                }
            }

            // Return to pool is now a suspend call.
            // use NonCancellable to ensure memory is recovered even if the scope is being cancelled.
            withContext(NonCancellable) {
                bitmapPool.put(bitmap)
            }
        }
    }
}
