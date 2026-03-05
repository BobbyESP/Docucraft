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
import com.composepdf.source.PdfSource
import com.composepdf.state.PdfViewerController
import com.composepdf.state.PdfViewerState
import com.composepdf.state.ViewerConfig
import com.composepdf.ui.PdfLayout

/**
 * A Compose-native PDF viewer.
 *
 * Supports:
 * - Local files, assets, URIs, byte arrays, input streams, and remote URLs
 * - Pinch-to-zoom and double-tap zoom
 * - Smooth fling scrolling
 * - Night mode (color inversion)
 * - State restoration across configuration changes
 *
 * @param source      The PDF to display
 * @param modifier    Modifier for the viewer container
 * @param state       Hoisted state — use [rememberPdfViewerState]
 * @param config      Viewer configuration
 * @param onPageChange  Callback when the current page changes (0-based)
 * @param onError       Callback when an error occurs
 * @param onDocumentLoad  Callback when the document finishes loading
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

    // Bake the current screen density into the config so the controller
    // can convert Dp → px without needing a CompositionLocal.
    val resolvedConfig = remember(config, density) {
        config.copy(density = density.density)
    }

    // Controller lifecycle: create once.
    // We use 'context' and 'state' as keys. If they change, we get a new controller.
    // Crucially, we do NOT include 'resolvedConfig' in the keys to avoid recreating
    // the controller (and reloading the PDF) just because a config parameter changed.
    val controller = remember(context, state) {
        PdfViewerController(context, state, resolvedConfig)
    }

    // Update the controller's config whenever it changes.
    LaunchedEffect(controller, resolvedConfig) {
        controller.updateConfig(resolvedConfig)
    }

    // Close the controller when the keys change (new instance was created above)
    // or when this composable leaves the composition entirely.
    DisposableEffect(controller) {
        state.controller = controller
        onDispose {
            state.controller = null
            controller.close()
        }
    }

    val renderedPages by controller.renderedPages.collectAsStateWithLifecycle()

    // Use rememberUpdatedState so these lambdas are always current without
    // restarting the LaunchedEffects that reference them.
    val latestOnPageChange by rememberUpdatedState(onPageChange)
    val latestOnError by rememberUpdatedState(onError)
    val latestOnDocumentLoad by rememberUpdatedState(onDocumentLoad)

    // Reload source when changed
    LaunchedEffect(source, controller) {
        controller.loadDocument(source)
    }

    LaunchedEffect(state.currentPage) { latestOnPageChange?.invoke(state.currentPage) }
    LaunchedEffect(state.error) { state.error?.let { latestOnError?.invoke(it) } }
    LaunchedEffect(state.pageCount) {
        if (state.pageCount > 0) latestOnDocumentLoad?.invoke(state.pageCount)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF424242)),
        contentAlignment = Alignment.Center
    ) {
        when {
            state.isLoading -> CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 6.dp
            )

            state.error != null -> Text(
                text = "Error loading PDF: ${state.error?.message}",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )

            state.isLoaded -> PdfLayout(
                pageSizes = controller.pageSizes,
                renderedPages = renderedPages,
                state = state,
                controller = controller,
                config = resolvedConfig
            )
        }
    }
}

/**
 * Creates and remembers a [PdfViewerState] that survives configuration changes.
 *
 * @param initialPage Zero-based page to open first
 * @param initialZoom Initial zoom level (1.0 = fit width)
 */
@Composable
fun rememberPdfViewerState(
    initialPage: Int = 0,
    initialZoom: Float = 1f
): PdfViewerState = rememberSaveable(saver = PdfViewerState.Saver) {
    PdfViewerState(initialPage, initialZoom)
}
