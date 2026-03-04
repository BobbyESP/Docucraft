package com.composepdf.state

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.composepdf.remote.RemotePdfState

@Stable
class PdfViewerState(
    initialPage: Int = 0,
    initialZoom: Float = 1f
) {
    var currentPage: Int by mutableIntStateOf(initialPage)
        internal set

    var pageCount: Int by mutableIntStateOf(0)
        internal set

    var zoom: Float by mutableFloatStateOf(initialZoom)
        internal set

    var panX: Float by mutableFloatStateOf(0f)
        internal set

    var panY: Float by mutableFloatStateOf(0f)
        internal set

    var isLoading: Boolean by mutableStateOf(true)
        internal set

    var error: Throwable? by mutableStateOf(null)
        internal set

    var isGestureActive: Boolean by mutableStateOf(false)
        internal set

    var remoteState: RemotePdfState by mutableStateOf(RemotePdfState.Idle)
        internal set

    /**
     * High-resolution tiles for the currently visible area.
     * Key: "pageIndex_tileRect"
     */
    val renderedTiles = mutableStateMapOf<String, Bitmap>()

    val isLoaded: Boolean
        get() = !isLoading && error == null && pageCount > 0

    val offset: Offset get() = Offset(panX, panY)

    internal fun reset() {
        currentPage = 0
        pageCount = 0
        zoom = 1f
        panX = 0f
        panY = 0f
        isLoading = true
        error = null
        isGestureActive = false
        renderedTiles.clear()
    }

    companion object {
        val Saver: Saver<PdfViewerState, *> = listSaver(
            save = { listOf(it.currentPage, it.zoom, it.panX, it.panY) },
            restore = {
                PdfViewerState(initialPage = it[0] as Int, initialZoom = it[1] as Float).also { s ->
                    s.panX = it[2] as Float
                    s.panY = it[3] as Float
                }
            }
        )
    }
}
