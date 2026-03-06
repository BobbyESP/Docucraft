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
 * A Compose-native PDF viewer with tiled high-resolution rendering.
 *
 * ## Features
 * - Local files, assets, URIs, byte arrays, input streams, and remote URLs
 * - Pinch-to-zoom and double-tap zoom (3-level cycle: fit → 2× → max)
 * - Smooth fling scrolling with exponential decay
 * - High-resolution tile rendering above zoom 1.1× — base page always visible beneath
 * - Night mode (full color inversion via a GPU-side color filter)
 * - In-memory LRU bitmap cache + persistent WebP tile disk cache
 * - State restoration across configuration changes via [rememberPdfViewerState]
 *
 * ## Usage
 * ```kotlin
 * val state = rememberPdfViewerState()
 * PdfViewer(
 *     source = PdfSource.Uri(uri),
 *     state  = state,
 *     config = ViewerConfig(maxZoom = 8f),
 *     onPageChange = { page -> println("Now on page $page") }
 * )
 * // Programmatic control from a coroutine:
 * state.animateScrollToPage(5)
 * state.zoomIn()
 * ```
 *
 * @param source          The PDF to display. See [PdfSource] for supported types.
 * @param modifier        Modifier for the viewer container.
 * @param state           Hoisted viewer state — use [rememberPdfViewerState].
 * @param config          Viewer configuration (zoom limits, fit mode, spacing, …).
 * @param onPageChange    Called whenever the most-visible page index changes (0-based).
 * @param onError         Called if the document fails to load.
 * @param onDocumentLoad  Called once when the document has loaded, with the total page count.
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

    // Controller lifecycle: created once per (context, state) pair.
    // Deliberately NOT keyed on resolvedConfig — config changes are delivered via
    // updateConfig() to avoid tearing down and reloading the whole document just
    // because e.g. fitMode changed.
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

    // rememberUpdatedState ensures callbacks are always the latest lambda without
    // restarting the LaunchedEffects that reference them.
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
 * The returned state can be used both to observe the viewer's current page, zoom,
 * and loading status, and to issue programmatic navigation commands
 * (e.g. [PdfViewerState.animateScrollToPage]).
 *
 * @param initialPage Zero-based index of the page to open first. Defaults to 0.
 * @param initialZoom Initial magnification level. Defaults to 1.0 (fit-to-viewport).
 */
@Composable
fun rememberPdfViewerState(
    initialPage: Int = 0,
    initialZoom: Float = 1f
): PdfViewerState = rememberSaveable(saver = PdfViewerState.Saver) {
    PdfViewerState(initialPage, initialZoom)
}
