package com.composepdf.state

import com.composepdf.remote.RemotePdfState
import com.composepdf.renderer.LoadedPdfDocument
import com.composepdf.renderer.RenderTrigger
import com.composepdf.source.PdfSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Sequences document/session mutations that should not live in [PdfViewerController].
 *
 * It keeps the controller as a thin façade while centralizing the delicate order of operations for:
 * - document reset/open/commit/failure publication
 * - viewport/layout refreshes
 * - high-resolution tile invalidation when geometry changes
 * - render triggers emitted after state has reached a coherent snapshot
 */
internal class ViewerSessionCoordinator(
    private val scope: CoroutineScope,
    private val state: PdfViewerState,
    private val viewportCoordinator: ViewerViewportCoordinator,
    private val updatePrefetchWindow: (Int) -> Unit,
    private val invalidateAll: () -> Unit,
    private val invalidateTiles: suspend () -> Unit,
    private val loadDocument: suspend (PdfSource, (RemotePdfState) -> Unit) -> LoadedPdfDocument,
    private val requestRender: (RenderTrigger) -> Unit,
    private val resetState: suspend () -> Unit = { state.reset() }
) {
    fun onConfigChanged(previousConfig: ViewerConfig, newConfig: ViewerConfig) {
        updatePrefetchWindow(newConfig.prefetchDistance)

        val requiresLayoutRefresh = previousConfig.fitMode != newConfig.fitMode ||
                previousConfig.pageSpacingPx != newConfig.pageSpacingPx

        if (!requiresLayoutRefresh) {
            requestRender(RenderTrigger.CONFIG_CHANGED)
            return
        }

        scope.launch {
            viewportCoordinator.onLayoutInputsChanged()
            invalidateTiles()
            requestRender(RenderTrigger.CONFIG_CHANGED)
        }
    }

    fun onViewportSizeChanged(width: Float, height: Float) {
        if (!viewportCoordinator.updateViewport(width, height)) return

        scope.launch {
            invalidateTiles()
            requestRender(RenderTrigger.VIEWPORT_CHANGED)
        }
    }

    fun loadDocument(source: PdfSource) {
        scope.launch {
            resetState()
            invalidateAll()

            try {
                val loadedDocument = loadDocument(source, state::updateRemoteDocumentState)
                viewportCoordinator.updatePageSizes(loadedDocument.pageSizes)
                state.completeDocumentLoad(loadedDocument.pageCount)
                requestRender(RenderTrigger.DOCUMENT_LOADED)
            } catch (error: Exception) {
                state.failDocumentLoad(error)
            }
        }
    }
}
