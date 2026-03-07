package com.composepdf.state

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.composepdf.cache.LruTileCache
import com.composepdf.remote.RemotePdfState
import com.composepdf.renderer.tiles.TileKey
import kotlinx.coroutines.Dispatchers
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
 * This model groups mutable state that belongs to the currently loaded PDF rather than to
 * viewport interaction itself:
 * - document lifecycle (`pageCount`, `isLoading`, `error`, `remoteState`)
 * - in-memory tile cache and its Compose-facing snapshots
 * - helpers used by the render pipeline to publish, prune and clear tiles
 *
 * By pulling these responsibilities out of [PdfViewerState], the public state object remains
 * focused on viewport interaction and imperative viewer APIs while render/document concerns stay
 * localized in a single mutable model.
 */
internal class ViewerSessionState {
    /** Total number of pages in the current document. */
    var pageCount: Int by mutableIntStateOf(0)

    /** Indicates whether the document is still loading or reopening. */
    var isLoading: Boolean by mutableStateOf(true)

    /** Last terminal error produced while loading or rendering the document session. */
    var error: Throwable? by mutableStateOf(null)

    /** State of the remote document loading if the source is remote. */
    var remoteState: RemotePdfState by mutableStateOf(RemotePdfState.Idle)

    /** True if the session has a ready document and no terminal error. */
    val isLoaded: Boolean get() = !isLoading && error == null && pageCount > 0

    /**
     * In-memory LRU cache for high-resolution tiles.
     *
     * Intentionally not cleared between gesture frames so previously rendered zoom levels remain
     * visible while the next tile set is produced.
     */
    private val tileCache = LruTileCache(onEvicted = { key ->
        removePublishedTile(key)
    })

    /** Revision counter observed by Compose to redraw pages when tile contents change. */
    var tileRevision by mutableIntStateOf(0)
        private set

    /** Snapshot of raw bitmap tiles, rebuilt incrementally after cache mutations. */
    private var tilesSnapshot: Map<String, Bitmap> = emptyMap()

    /** Compose-friendly snapshot mirroring [tilesSnapshot], already grouped by page for fast draws. */
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

        val updatedTiles = tilesSnapshot.toMutableMap().apply {
            put(key, bitmap)
        }
        tilesSnapshot = updatedTiles

        val updatedPageTiles = publishedTilesByPage.toMutableMap()
        val pageTiles = updatedPageTiles[tileKey.pageIndex].orEmpty()
            .filterNot { it.cacheKey == key }
            .toMutableList()
        pageTiles += publishedTile
        updatedPageTiles[tileKey.pageIndex] =
            pageTiles.sortedWith(compareBy({ it.tileKey.rect.top }, { it.tileKey.rect.left }))
        publishedTilesByPage = updatedPageTiles
        tileRevision++
    }

    suspend fun pruneTiles(predicate: (String) -> Boolean) =
        withContext(Dispatchers.Main.immediate) {
            val keysToRemove = tilesSnapshot.keys.filter(predicate)
            if (keysToRemove.isEmpty()) return@withContext

            keysToRemove.forEach { key ->
                tileCache.remove(key)
                removePublishedTile(key, incrementRevision = false)
            }
            tileRevision++
        }

    suspend fun clearTiles() = withContext(Dispatchers.Main.immediate) {
        tileCache.evictAll()
        tilesSnapshot = emptyMap()
        publishedTilesByPage = emptyMap()
        tileRevision++
    }

    private fun removePublishedTile(key: String, incrementRevision: Boolean = false) {
        val removedBitmap = tilesSnapshot[key]
        if (removedBitmap == null) return

        tilesSnapshot = tilesSnapshot.toMutableMap().apply { remove(key) }
        val tileKey = TileKey.fromCacheKey(key)
        if (tileKey != null) {
            val updatedByPage = publishedTilesByPage.toMutableMap()
            val remainingForPage = updatedByPage[tileKey.pageIndex].orEmpty()
                .filterNot { it.cacheKey == key }
            if (remainingForPage.isEmpty()) updatedByPage.remove(tileKey.pageIndex)
            else updatedByPage[tileKey.pageIndex] = remainingForPage
            publishedTilesByPage = updatedByPage
        }

        if (incrementRevision) tileRevision++
    }
}
