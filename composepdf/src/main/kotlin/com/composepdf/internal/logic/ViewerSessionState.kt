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
 * Represents a rendered segment of a PDF page that has been processed and is ready for display.
 *
 * This data class serves as the bridge between the internal bitmap cache and the UI layer,
 * providing the necessary metadata and the Compose-compatible [ImageBitmap] for rendering.
 *
 * @property cacheKey The unique string identifier used to track this tile in the memory cache.
 * @property tileKey The decomposed metadata of the tile, including its page index and position.
 * @property imageBitmap The actual visual content of the tile converted for use in Compose.
 */
internal data class PublishedTile(
    val cacheKey: String,
    val tileKey: TileKey,
    val imageBitmap: ImageBitmap
)

/**
 * Manages the internal state and lifecycle of a PDF viewing session.
 *
 * This class serves as the central state holder for a specific document session, tracking
 * document loading progress, error states, and remote synchronization status. It integrates
 * with Jetpack Compose via [mutableStateOf] to provide observable updates to the UI.
 *
 * Key responsibilities include:
 * - Tracking document metadata such as [pageCount] and [isLoading] status.
 * - Managing an in-memory cache of rendered PDF tiles using [MemoryCache].
 * - Synchronizing tile snapshots for UI rendering and ensuring efficient memory usage
 *   by returning evicted bitmaps to the [BitmapPool].
 * - Providing telemetry snapshots for performance monitoring.
 *
 * All state modifications involving UI-bound snapshots are performed on the Main dispatcher
 * to ensure consistency.
 *
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
