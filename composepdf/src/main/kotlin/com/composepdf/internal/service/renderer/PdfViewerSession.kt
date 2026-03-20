package com.composepdf.internal.service.renderer

import android.content.Context
import android.graphics.Bitmap
import com.composepdf.PdfSource
import com.composepdf.PdfViewerState
import com.composepdf.RemotePdfState
import com.composepdf.ViewerConfig
import com.composepdf.internal.logic.ViewerViewportCoordinator
import com.composepdf.internal.logic.tiles.TilePlanner
import com.composepdf.internal.service.cache.TileDiskCache
import com.composepdf.internal.service.cache.bitmap.BitmapHousekeeper
import com.composepdf.internal.service.cache.bitmap.BitmapPool
import com.composepdf.internal.service.pdf.DocumentResult
import com.composepdf.internal.service.pdf.PageRenderer
import com.composepdf.internal.service.pdf.PdfDocumentManager
import com.composepdf.internal.service.pdf.PdfDocumentSession
import com.composepdf.internal.util.longLivedContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable

/**
 * Groups the active document session and the render infrastructure of the viewer.
 *
 * This object owns the lifecycle of:
 * - persistent tile disk cache
 * - document opening/session state
 * - bitmap housekeeping
 * - render scheduler
 * - tile planning/render pipeline
 *
 * `PdfViewerController` delegates document/render concerns here and stays focused on public
 * orchestration and state-facing APIs.
 */
internal class PdfViewerSession(
    context: Context,
    scope: CoroutineScope,
    state: PdfViewerState,
    bitmapPool: BitmapPool,
    viewportCoordinator: ViewerViewportCoordinator,
    configProvider: () -> ViewerConfig
) : Closeable {
    private val appContext = context.longLivedContext()
    private val tileDiskCache = TileDiskCache(appContext.cacheDir.resolve("pdf_tiles"))
    private val documentManager = PdfDocumentManager(appContext)
    private val documentSession = PdfDocumentSession(appContext, documentManager, tileDiskCache)
    private val pageRenderer = PageRenderer(bitmapPool)
    private var renderedPagesProvider: () -> Map<Int, Bitmap> = { emptyMap() }
    private val telemetry = RenderTelemetry()
    private val bitmapHousekeeper = BitmapHousekeeper(
        scope = scope,
        state = state,
        renderedPagesProvider = { renderedPagesProvider() },
        bitmapPool = bitmapPool
    )
    private val renderScheduler = RenderScheduler(
        documentManager = documentManager,
        pageRenderer = pageRenderer,
        cache = bitmapHousekeeper.bitmapCache,
        viewerState = state,
        bitmapPool = bitmapPool,
        tileDiskCache = tileDiskCache,
        telemetry = telemetry
    )
    private val renderPipeline = ViewerRenderPipeline(
        scope = scope,
        state = state,
        viewportCoordinator = viewportCoordinator,
        renderScheduler = renderScheduler,
        tilePlanner = TilePlanner(PageRenderer.TILE_SIZE),
        telemetry = telemetry,
        configProvider = configProvider,
        isDocumentOpen = { documentManager.isOpen }
    )

    val renderedPages: StateFlow<Map<Int, Bitmap>> = renderScheduler.renderedPages
    val renderTelemetry: StateFlow<RenderTelemetrySnapshot> = telemetry.snapshot

    init {
        renderedPagesProvider = { renderScheduler.renderedPages.value }
    }

    fun recentRenderEvents(limit: Int = 50): List<RenderTelemetryEvent> =
        telemetry.recentEvents(limit)

    fun updatePrefetchWindow(prefetchDistance: Int) {
        renderScheduler.prefetchWindow = prefetchDistance
    }

    suspend fun loadDocument(
        source: PdfSource,
        onRemoteState: (RemotePdfState) -> Unit = {}
    ): DocumentResult {
        val loadedDocument = documentSession.open(source, onRemoteState)
        renderPipeline.onDocumentLoaded(loadedDocument.documentKey)
        return loadedDocument
    }

    suspend fun invalidateTiles() {
        renderPipeline.invalidateTiles()
    }

    fun recordPanDelta(panDeltaY: Float) {
        renderPipeline.recordPanDelta(panDeltaY)
    }

    fun requestRenderForVisiblePages(trigger: RenderTrigger = RenderTrigger.PROGRAMMATIC) {
        renderPipeline.requestRenderForVisiblePages(trigger)
    }

    fun invalidateAll() {
        renderScheduler.invalidateAll()
    }

    override fun close() {
        renderScheduler.close()
        documentManager.close()
        bitmapHousekeeper.clear()
    }
}
