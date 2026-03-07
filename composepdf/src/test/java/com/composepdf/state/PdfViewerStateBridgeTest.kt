package com.composepdf.state

import android.util.Size
import androidx.compose.ui.geometry.Offset
import com.composepdf.remote.RemotePdfState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfViewerStateBridgeTest {

    @Test
    fun setFitMode_updatesControllerConfigThroughBridge() {
        val state = PdfViewerState()
        val bridge = FakeStateBridge()
        state.controller = bridge

        state.setFitMode(FitMode.HEIGHT)

        assertEquals(FitMode.HEIGHT, bridge.viewerConfig.fitMode)
    }

    @Test
    fun setZoom_delegatesAnimatedZoomAroundViewportCenter() {
        val state = PdfViewerState().apply { zoom = 1f }
        val bridge = FakeStateBridge(viewportWidth = 600f, viewportHeight = 800f)
        state.controller = bridge

        state.setZoom(2f)

        assertEquals(2f, bridge.lastAnimatedZoom, 0.001f)
        assertEquals(Offset(300f, 400f), bridge.lastPivot)
    }

    @Test
    fun scrollToPage_centersTargetPageAndRequestsRender() {
        val state = PdfViewerState().apply {
            pageCount = 3
            zoom = 1f
        }
        val bridge = FakeStateBridge(viewportWidth = 500f, viewportHeight = 500f)
        state.controller = bridge

        state.scrollToPage(1)

        assertEquals(1, state.currentPage)
        assertTrue(bridge.clampPanCalled)
        assertTrue(bridge.renderRequested)
        assertEquals(-520f, state.panY, 0.001f)
    }

    @Test
    fun sessionHelpers_updatePublicLifecycleFlags() {
        val state = PdfViewerState()
        val remote =
            RemotePdfState.Downloading(progress = 0.5f, bytesDownloaded = 5, totalBytes = 10)
        val failure = IllegalStateException("broken")

        state.beginDocumentLoad()
        state.updateRemoteDocumentState(remote)
        state.completeDocumentLoad(pageCount = 7)

        assertEquals(7, state.pageCount)
        assertEquals(remote, state.remoteState)
        assertTrue(state.isLoaded)
        assertFalse(state.isLoading)
        assertNull(state.error)

        state.failDocumentLoad(failure)

        assertFalse(state.isLoaded)
        assertEquals(failure, state.error)
        assertFalse(state.isLoading)
    }

    private class FakeStateBridge(
        override val viewportWidth: Float = 500f,
        override val viewportHeight: Float = 500f,
        override var viewerConfig: ViewerConfig = ViewerConfig()
    ) : PdfViewerStateControllerBridge {
        override val pageSizes: List<Size> = listOf(Size(1, 1), Size(1, 1), Size(1, 1))

        var lastAnimatedZoom: Float = 0f
        var lastPivot: Offset = Offset.Zero
        var renderRequested = false
        var clampPanCalled = false

        override fun pageHeightPx(index: Int): Float = 500f
        override fun pageWidthPx(index: Int): Float = 500f
        override fun pageTopDocY(index: Int): Float = index * 520f
        override fun visiblePageIndices(): IntRange = 0..0
        override fun isPointOverPage(point: Offset): Boolean = true
        override fun computeCenteredPanForPage(pageIndex: Int): Pair<Float, Float> =
            0f to (pageIndex * -520f)

        override fun computeFitDocumentZoom(): Float = 1f
        override fun computeFitPageZoom(pageIndex: Int): Float = 1f
        override fun onViewportSizeChanged(width: Float, height: Float) = Unit
        override fun requestRenderForVisiblePages() {
            renderRequested = true
        }

        override fun clampPan() {
            clampPanCalled = true
        }

        override fun onGestureStart() = Unit
        override fun onGestureEnd() = Unit
        override fun onGestureUpdate(zoomChange: Float, panDelta: Offset, pivot: Offset) = Unit
        override fun onAnimatedZoomFrame(targetZoom: Float, pivot: Offset) {
            lastAnimatedZoom = targetZoom
            lastPivot = pivot
        }

        override fun updateConfig(newConfig: ViewerConfig) {
            viewerConfig = newConfig
        }
    }
}
