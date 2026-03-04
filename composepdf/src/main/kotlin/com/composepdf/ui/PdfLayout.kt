package com.composepdf.ui

import android.graphics.Bitmap
import android.util.Size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import com.composepdf.gesture.pdfGestures
import com.composepdf.state.PdfViewerController
import com.composepdf.state.PdfViewerState
import com.composepdf.state.ViewerConfig
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/**
 * Core layout for the PDF viewer.
 *
 * It uses a custom [Layout] to position pages absolutely based on the [PdfViewerController]'s geometry model.
 * This ensures pixel-perfect positioning and efficient culling of non-visible pages.
 *
 * @param pageSizes List of pre-calculated page dimensions at zoom 1.0.
 * @param renderedPages State flow of the base low-resolution bitmaps for each page.
 * @param state The current UI state (zoom, pan, tiles).
 * @param controller The logic engine for coordinate conversion and render requests.
 * @param config Viewer configuration (night mode, indicators, etc.).
 * @param modifier Layout modifier.
 */
@Composable
internal fun PdfLayout(
    pageSizes: List<Size>,
    renderedPages: Map<Int, Bitmap>,
    state: PdfViewerState,
    controller: PdfViewerController,
    config: ViewerConfig,
    modifier: Modifier = Modifier
) {
    // 1. Determine visible pages. This only triggers recomposition when the set of visible indices changes.
    val visiblePages by remember(controller) {
        derivedStateOf { controller.visiblePageIndices() }
    }

    // 2. Orchestrate rendering requests. Triggers when visibility, gestures, or zoom change.
    LaunchedEffect(controller) {
        snapshotFlow {
            Triple(controller.visiblePageIndices(), state.isGestureActive, state.zoom)
        }.collectLatest { 
            controller.requestRenderForVisiblePages()
        }
    }

    // 3. Prepare expensive objects.
    val colorFilter = remember(config.isNightModeEnabled) {
        if (config.isNightModeEnabled) {
            ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        } else null
    }

    Layout(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { size ->
                controller.onViewportSizeChanged(size.width.toFloat(), size.height.toFloat())
            }
            .pdfGestures(
                state = state,
                controller = controller,
                config = config,
                enabled = state.isLoaded
            ),
        content = {
            for (index in visiblePages) {
                key(index) {
                    val size = pageSizes.getOrNull(index)
                    if (size != null) {
                        PdfPage(
                            state = state,
                            bitmap = renderedPages[index],
                            pageIndex = index,
                            aspectRatio = size.width.toFloat() / size.height.toFloat(),
                            showLoadingIndicator = config.isLoadingIndicatorVisible,
                            fitMode = config.fitMode,
                            colorFilter = colorFilter,
                            modifier = Modifier.layoutId(index).fillMaxSize()
                        )
                    }
                }
            }
        }
    ) { measurables, constraints ->
        val vpWidth = controller.viewportWidth
        if (measurables.isEmpty() || vpWidth <= 0f) {
            return@Layout layout(constraints.maxWidth, constraints.maxHeight) {}
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            measurables.forEach { measurable ->
                val pageIndex = measurable.layoutId as? Int ?: return@forEach
                
                val docTopY = controller.pageTopDocY(pageIndex)
                val docHeight = controller.pageHeightPx(pageIndex)

                val screenW = (vpWidth * state.zoom).roundToInt().coerceAtLeast(1)
                val screenH = (docHeight * state.zoom).roundToInt().coerceAtLeast(1)

                val x = state.panX.roundToInt()
                val y = (docTopY * state.zoom + state.panY).roundToInt()

                measurable.measure(Constraints.fixed(screenW, screenH)).place(x, y)
            }
        }
    }
}
