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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        imageBitmapSnapshot.remove(key)
    })

    /** Revision counter observed by Compose to redraw pages when tile contents change. */
    var tileRevision by mutableIntStateOf(0)
        private set

    /** Snapshot of raw bitmap tiles, rebuilt only after cache mutations. */
    private var tilesSnapshot: Map<String, Bitmap> = emptyMap()

    /** Compose-friendly snapshot mirroring [tilesSnapshot]. */
    private var imageBitmapSnapshot: MutableMap<String, ImageBitmap> = mutableMapOf()

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

    fun getAllImageBitmapTiles(): Map<String, ImageBitmap> = imageBitmapSnapshot

    suspend fun putTile(key: String, bitmap: Bitmap) = withContext(Dispatchers.Main.immediate) {
        tileCache.put(key, bitmap)
        imageBitmapSnapshot[key] = bitmap.asImageBitmap()
        imageBitmapSnapshot.keys.retainAll(tileCache.snapshot().keys)
        tilesSnapshot = tileCache.snapshot()
        tileRevision++
    }

    suspend fun pruneTiles(predicate: (String) -> Boolean) = withContext(Dispatchers.Main.immediate) {
        val snapshot = tileCache.snapshot()
        var changed = false
        snapshot.keys.forEach { key ->
            if (predicate(key)) {
                tileCache.remove(key)
                changed = true
            }
        }
        if (changed) {
            val remaining = tileCache.snapshot()
            tilesSnapshot = remaining
            imageBitmapSnapshot = remaining.mapValuesTo(mutableMapOf()) { (_, bmp) ->
                bmp.asImageBitmap()
            }
            tileRevision++
        }
    }

    suspend fun clearTiles() = withContext(Dispatchers.Main.immediate) {
        tileCache.evictAll()
        tilesSnapshot = emptyMap()
        imageBitmapSnapshot = mutableMapOf()
        tileRevision++
    }
}
