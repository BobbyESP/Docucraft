package com.composepdf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composepdf.cache.bitmap.BitmapPool
import com.composepdf.source.PdfSource
import com.composepdf.state.PdfViewerController
import com.composepdf.state.PdfViewerState
import com.composepdf.state.ViewerConfig
import com.composepdf.ui.PdfLayout

/**
 * A Jetpack Compose component for displaying PDF documents with support for high-resolution
 * tiled rendering, pinch-to-zoom, and smooth scrolling.
 *
 * This viewer efficiently handles large documents by using memory-managed bitmap pooling
 * and asynchronous page loading.
 *
 * @param source The [PdfSource] representing the document to be displayed (e.g., file, asset, or URI).
 * @param modifier The [Modifier] to be applied to the viewer's container.
 * @param state The [PdfViewerState] used to control the viewer and observe its current status.
 * @param config The [ViewerConfig] used to customize rendering quality and layout behavior.
 * @param onPageChange An optional callback invoked when the current visible page changes.
 * @param onError An optional callback invoked when an error occurs during loading or rendering.
 * @param onDocumentLoad An optional callback invoked when the document is successfully loaded,
 * providing the total number of pages.
 */
@Composable
fun PdfViewer(
    source: PdfSource,
    modifier: Modifier = Modifier,
    state: PdfViewerState = rememberPdfViewerState(),
    config: ViewerConfig = ViewerConfig(),
    onPageChange: ((Int) -> Unit)? = null,
    onError: ((Throwable) -> Unit)? = null,
    onDocumentLoad: ((pageCount: Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val resolvedConfig = remember(config, density) {
        config.copy(density = density.density)
    }

    val controller = remember(context, state) {
        PdfViewerController(context, state, resolvedConfig)
    }

    LaunchedEffect(controller, resolvedConfig) {
        controller.updateConfig(resolvedConfig)
    }

    DisposableEffect(controller) {
        state.controller = controller.stateBridge
        onDispose {
            state.controller = null
            controller.close()
        }
    }

    val renderedPages by controller.renderedPages.collectAsStateWithLifecycle()

    val latestOnPageChange by rememberUpdatedState(onPageChange)
    val latestOnError by rememberUpdatedState(onError)
    val latestOnDocumentLoad by rememberUpdatedState(onDocumentLoad)

    LaunchedEffect(source, controller) { controller.loadDocument(source) }
    LaunchedEffect(state.currentPage) { latestOnPageChange?.invoke(state.currentPage) }
    LaunchedEffect(state.error) { state.error?.let { latestOnError?.invoke(it) } }
    LaunchedEffect(state.pageCount) {
        if (state.pageCount > 0) latestOnDocumentLoad?.invoke(state.pageCount)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PdfViewerDefaults.ViewerBackground),
        contentAlignment = Alignment.Center
    ) {
        when {
            state.isLoading -> CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 6.dp
            )

            state.error != null -> Text(
                text = state.error?.let { err ->
                    val type = err::class.simpleName ?: "Error"
                    "$type: ${err.message}"
                } ?: "Unknown error",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )

            state.isLoaded -> PdfLayout(
                pageSizes = controller.layoutController.pageSizes,
                renderedPages = renderedPages,
                state = state,
                layoutController = controller.layoutController,
                gestureController = controller.gestureController,
                config = resolvedConfig
            )
        }
    }
}

/**
 * Creates and remembers a [PdfViewerState] that survives configuration changes.
 * 
 * In this industrial version, the [BitmapPool] is shared globally or tied to the 
 * viewer's lifetime to ensure all bitmaps are tracked and recycled.
 */
@Composable
fun rememberPdfViewerState(
    initialPage: Int = 0,
    initialZoom: Float = 1f
): PdfViewerState {
    // Global or context-scoped BitmapPool for the viewer engine.
    val bitmapPool = remember { BitmapPool() }
    
    return rememberSaveable(
        saver = PdfViewerState.saver(bitmapPool)
    ) {
        PdfViewerState(initialPage, initialZoom, bitmapPool)
    }
}
