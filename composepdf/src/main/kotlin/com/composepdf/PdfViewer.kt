package com.composepdf

import android.util.Size
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composepdf.gesture.pdfGestures
import com.composepdf.source.PdfSource
import com.composepdf.state.PdfViewerController
import com.composepdf.state.PdfViewerState
import com.composepdf.state.ViewerConfig
import com.composepdf.ui.PdfLayout

/**
 * A Compose-native PDF viewer component.
 * 
 * This composable provides a full-featured PDF viewing experience with support for:
 * - Multiple PDF sources (File, Asset, URI, InputStream, ByteArray)
 * - Pinch-to-zoom and double-tap zoom
 * - Smooth scrolling with optional page snapping
 * - Vertical and horizontal scroll directions
 * - Night mode (color inversion)
 * - State restoration across configuration changes
 * 
 * ## Basic Usage
 * 
 * ```kotlin
 * @Composable
 * fun MyScreen() {
 *     val state = rememberPdfViewerState()
 *     
 *     PdfViewer(
 *         source = PdfSource.FromAsset("sample.pdf"),
 *         state = state,
 *         modifier = Modifier.fillMaxSize()
 *     )
 * }
 * ```
 * 
 * ## With Configuration
 * 
 * ```kotlin
 * PdfViewer(
 *     source = PdfSource.FromUri(documentUri),
 *     state = rememberPdfViewerState(),
 *     config = ViewerConfig(
 *         scrollDirection = ScrollDirection.HORIZONTAL,
 *         isPageSnappingEnabled = true,
 *         isNightModeEnabled = true
 *     ),
 *     onPageChange = { page -> 
 *         println("Now viewing page $page")
 *     }
 * )
 * ```
 * 
 * @param source The PDF source to display (required)
 * @param modifier Modifier for the viewer container
 * @param state The viewer state (use [rememberPdfViewerState])
 * @param config Configuration options for the viewer
 * @param onPageChange Callback when the current page changes
 * @param onError Callback when an error occurs
 * @param onDocumentLoad Callback when the document is loaded successfully
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
    
    // Create controller
    val controller = remember(context, state, config) {
        PdfViewerController(context, state, config)
    }
    
    // Track page sizes for aspect ratio calculations
    val pageSizes = remember { mutableStateListOf<Size>() }
    
    // Collect rendered pages
    val renderedPages by controller.renderedPages.collectAsStateWithLifecycle()

    // Wrap callbacks in rememberUpdatedState so LaunchedEffect always sees
    // the latest lambda without needing to restart when it changes.
    val currentOnPageChange by rememberUpdatedState(onPageChange)
    val currentOnError by rememberUpdatedState(onError)
    val currentOnDocumentLoad by rememberUpdatedState(onDocumentLoad)

    // Load document when source changes
    LaunchedEffect(source) {
        controller.loadDocument(source)
    }
    
    // Notify callbacks
    LaunchedEffect(state.currentPage) {
        currentOnPageChange?.invoke(state.currentPage)
    }
    
    LaunchedEffect(state.error) {
        state.error?.let { error ->
            currentOnError?.invoke(error)
        }
    }
    
    LaunchedEffect(state.pageCount) {
        if (state.pageCount > 0) {
            currentOnDocumentLoad?.invoke(state.pageCount)

            // Load real page sizes from the document instead of assuming A4 for all pages
            val sizes = controller.getPageSizes()
            pageSizes.clear()
            pageSizes.addAll(sizes)
        }
    }
    
    // Cleanup on dispose
    DisposableEffect(controller) {
        onDispose {
            controller.close()
        }
    }
    
    // UI
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF424242)) // PDF viewer background
            .pdfGestures(
                state = state,
                controller = controller,
                config = config,
                enabled = state.isLoaded
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 6.dp
                )
            }

            state.error != null -> {
                Text(
                    text = "Error loading PDF: ${state.error?.message}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            state.isLoaded -> {
                PdfLayout(
                    pageCount = state.pageCount,
                    pageSizes = pageSizes,
                    renderedPages = renderedPages,
                    state = state,
                    controller = controller,
                    config = config
                )
            }
        }
    }
}

/**
 * Remembers and saves [PdfViewerState] across configuration changes.
 * 
 * The state is automatically restored when the composable is recomposed
 * after a configuration change (e.g., rotation).
 * 
 * @param initialPage The initial page to display (zero-based)
 * @param initialZoom The initial zoom level (1.0 = 100%)
 * @return A remembered and saveable [PdfViewerState]
 */
@Composable
fun rememberPdfViewerState(
    initialPage: Int = 0,
    initialZoom: Float = 1f
): PdfViewerState = rememberSaveable(saver = PdfViewerState.Saver) {
    PdfViewerState(initialPage, initialZoom)
}
