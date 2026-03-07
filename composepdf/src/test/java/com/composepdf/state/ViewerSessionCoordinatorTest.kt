package com.composepdf.state

import android.util.Size
import androidx.compose.ui.unit.dp
import com.composepdf.renderer.LoadedPdfDocument
import com.composepdf.renderer.RenderTrigger
import com.composepdf.source.PdfSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ViewerSessionCoordinatorTest {

    @Test
    fun onConfigChanged_withLayoutImpact_invalidatesTilesAndRequestsRender() = runBlocking {
        val state = PdfViewerState().apply { pageCount = 1 }
        val viewportCoordinator = ViewerViewportCoordinator(
            state = state,
            configProvider = { ViewerConfig() }
        )

        var invalidatedTiles = 0
        val renderTriggers = mutableListOf<RenderTrigger>()

        val coordinator = ViewerSessionCoordinator(
            scope = CoroutineScope(Dispatchers.Unconfined),
            state = state,
            viewportCoordinator = viewportCoordinator,
            updatePrefetchWindow = {},
            invalidateAll = {},
            invalidateTiles = { invalidatedTiles++ },
            loadDocument = { _, _ -> error("unused") },
            requestRender = { renderTriggers += it },
            resetState = {}
        )

        coordinator.onConfigChanged(
            previousConfig = ViewerConfig(fitMode = FitMode.WIDTH, pageSpacing = 12.dp),
            newConfig = ViewerConfig(fitMode = FitMode.BOTH, pageSpacing = 12.dp)
        )

        withTimeout(1_000) {
            while (renderTriggers.isEmpty()) yield()
        }

        assertEquals(1, invalidatedTiles)
        assertEquals(listOf(RenderTrigger.CONFIG_CHANGED), renderTriggers)
    }

    @Test
    fun loadDocument_resetsStateCommitsPagesAndRequestsRender() = runBlocking {
        val state = PdfViewerState().apply {
            pageCount = 4
            zoom = 2f
            panX = 10f
            panY = 20f
            isGestureActive = true
            error = IllegalStateException("stale")
        }
        val viewportCoordinator = ViewerViewportCoordinator(
            state = state,
            configProvider = { ViewerConfig() }
        )
        var invalidatedAll = 0
        val renderTriggers = mutableListOf<RenderTrigger>()

        val coordinator = ViewerSessionCoordinator(
            scope = CoroutineScope(Dispatchers.Unconfined),
            state = state,
            viewportCoordinator = viewportCoordinator,
            updatePrefetchWindow = {},
            invalidateAll = { invalidatedAll++ },
            invalidateTiles = {},
            loadDocument = { _, remoteState ->
                remoteState(
                    com.composepdf.remote.RemotePdfState.Downloading(
                        progress = 0f,
                        bytesDownloaded = 0,
                        totalBytes = 100
                    )
                )
                LoadedPdfDocument(
                    documentKey = "doc",
                    pageSizes = listOf(Size(600, 800), Size(600, 800)),
                    pageCount = 2
                )
            },
            requestRender = { renderTriggers += it },
            resetState = {
                state.currentPage = 0
                state.zoom = 1f
                state.panX = 0f
                state.panY = 0f
                state.isGestureActive = false
                state.beginDocumentLoad()
            }
        )

        coordinator.loadDocument(PdfSource.File(File("sample.pdf")))

        withTimeout(1_000) {
            while (renderTriggers.isEmpty() || !state.isLoaded) yield()
        }

        assertEquals(1, invalidatedAll)
        assertEquals(2, state.pageCount)
        assertTrue(state.isLoaded)
        assertEquals(1f, state.zoom, 0.001f)
        assertEquals(0f, state.panX, 0.001f)
        assertEquals(0f, state.panY, 0.001f)
        assertEquals(0, state.currentPage)
        assertTrue(!state.isGestureActive)
        assertEquals(null, state.error)
        assertEquals(listOf(RenderTrigger.DOCUMENT_LOADED), renderTriggers)
    }
}
