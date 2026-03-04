package com.composepdf.state

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.composepdf.remote.RemotePdfState

/**
 * A hoistable state object that manages the UI state and navigation for a PDF viewer.
 *
 * This class tracks document navigation (current page, zoom level, and pan offsets) and
 * manages a persistent [LruCache] for high-resolution tiles. The cache ensures visual
 * stability and smooth transitions during pinch-to-zoom and panning gestures.
 *
 * Use this state to programmatically control the viewer or to react to user-driven
 * changes in the document's viewport.
 *
 * @param initialPage The index of the page to be displayed initially. Defaults to 0.
 * @param initialZoom The initial magnification level. Defaults to 1.0f (fit-to-width).
 */
@Stable
class PdfViewerState(
    initialPage: Int = 0,
    initialZoom: Float = 1f
) {
    /** The index of the current page most visible in the viewport. */
    var currentPage: Int by mutableIntStateOf(initialPage)
        internal set

    /** Total number of pages in the current document. */
    var pageCount: Int by mutableIntStateOf(0)
        internal set

    /** Current magnification level. 1.0f means fit-to-width. */
    var zoom: Float by mutableFloatStateOf(initialZoom)
        internal set

    /** Horizontal translation offset in screen pixels. */
    var panX: Float by mutableFloatStateOf(0f)
        internal set

    /** Vertical translation offset in screen pixels. */
    var panY: Float by mutableFloatStateOf(0f)
        internal set

    /** Indicates if the document or pages are currently being loaded/rendered. */
    var isLoading: Boolean by mutableStateOf(true)
        internal set

    /** Stores any error encountered during the PDF lifecycle. */
    var error: Throwable? by mutableStateOf(null)
        internal set

    /** True if a user gesture (pinch, pan) is currently active. */
    var isGestureActive: Boolean by mutableStateOf(false)
        internal set

    /** State of the remote document loading if applicable. */
    var remoteState: RemotePdfState by mutableStateOf(RemotePdfState.Idle)
        internal set

    /**
     * Internal cache for high-resolution tiles.
     * Persists through gestures to provide "Double Buffering" visual stability.
     */
    private val tileCache = LruCache<String, Bitmap>(400)

    /**
     * Revision counter to notify Compose when the tile cache is updated.
     */
    var tileRevision by mutableIntStateOf(0)
        private set

    /** Retrieves a tile from the cache. */
    fun getTile(key: String): Bitmap? = tileCache.get(key)

    /** Stores a rendered tile and triggers UI update. */
    fun putTile(key: String, bitmap: Bitmap) {
        tileCache.put(key, bitmap)
        tileRevision++
    }

    /** Returns a snapshot of all currently cached tiles. */
    fun getAllTiles(): Map<String, Bitmap> = tileCache.snapshot()

    /**
     * Removes tiles that match the given predicate.
     * Used for selective invalidation (e.g., zoom level pruning).
     */
    fun pruneTiles(predicate: (String) -> Boolean) {
        val snapshot = tileCache.snapshot()
        var changed = false
        snapshot.keys.forEach { key ->
            if (predicate(key)) {
                tileCache.remove(key)
                changed = true
            }
        }
        if (changed) tileRevision++
    }

    /** Clears all high-resolution tiles. */
    fun clearTiles() {
        tileCache.evictAll()
        tileRevision++
    }

    /** True if a document is loaded and ready for interaction. */
    val isLoaded: Boolean get() = !isLoading && error == null && pageCount > 0

    /** Current pan offset as a Compose Offset. */
    val offset: Offset get() = Offset(panX, panY)

    /** Resets the state to initial values. */
    internal fun reset() {
        currentPage = 0; pageCount = 0; zoom = 1f; panX = 0f; panY = 0f
        isLoading = true; error = null; isGestureActive = false
        clearTiles()
    }

    companion object {
        /** Saver for persisting state across configuration changes. */
        val Saver: Saver<PdfViewerState, *> = listSaver(
            save = { listOf(it.currentPage, it.zoom, it.panX, it.panY) },
            restore = {
                PdfViewerState(initialPage = it[0] as Int, initialZoom = it[1] as Float).also { s ->
                    s.panX = it[2] as Float; s.panY = it[3] as Float
                }
            }
        )
    }
}
