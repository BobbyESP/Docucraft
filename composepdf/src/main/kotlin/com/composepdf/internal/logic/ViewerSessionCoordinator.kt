package com.composepdf.internal.logic

import com.composepdf.PdfSource
import com.composepdf.PdfViewerState
import com.composepdf.RemotePdfState
import com.composepdf.RenderTrigger
import com.composepdf.ViewerConfig
import com.composepdf.internal.service.pdf.DocumentResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Coordinates the lifecycle and state mutations of a PDF viewing session.
 *
 * This class acts as the central orchestrator for complex operations that involve multiple
 * components, ensuring that state updates, viewport adjustments, and rendering triggers
 * occur in the correct sequence. It handles:
 * - Loading new documents and managing the transition between document states.
 * - Responding to configuration changes (e.g., fit mode, spacing) by refreshing layouts.
 * - Processing viewport size changes and invalidating high-resolution tiles.
 * - Emitting render triggers only after the internal state has reached a coherent snapshot.
 *
 * By centralizing these orchestration concerns, it allows [PdfViewerState] to remain a pure
 * state container and the controller to act as a thin façade.
 */
internal class ViewerSessionCoordinator(
    private val scope: CoroutineScope,
    private val state: PdfViewerState,
    private val viewportCoordinator: ViewerViewportCoordinator,
    private val updatePrefetchWindow: (Int) -> Unit,
    private val invalidateAll: () -> Unit,
    private val invalidateTiles: suspend () -> Unit,
    private val loadDocument: suspend (PdfSource, (RemotePdfState) -> Unit) -> DocumentResult,
    private val requestRender: (RenderTrigger) -> Unit,
    private val resetState: suspend () -> Unit = { state.reset() }
) {
    fun onConfigChanged(previousConfig: ViewerConfig, newConfig: ViewerConfig) {
        updatePrefetchWindow(newConfig.prefetchDistance)

        val requiresLayoutRefresh = previousConfig.fitMode != newConfig.fitMode ||
                previousConfig.pageSpacingPx != newConfig.pageSpacingPx ||
                previousConfig.scrollDirection != newConfig.scrollDirection

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
