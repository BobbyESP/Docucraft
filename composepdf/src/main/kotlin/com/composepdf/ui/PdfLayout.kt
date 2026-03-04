package com.composepdf.ui

import android.graphics.Bitmap
import android.util.Size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
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
 * ## Architecture
 * Uses a custom [Layout] to position pages absolutely based on the [PdfViewerController]'s geometry model.
 * This ensures pixel-perfect positioning without accumulation errors and efficient culling.
 *
 * ## Performance Optimizations
 * 1. **Virtual Rendering**: Only composed pages are measured and placed.
 * 2. **Stable Keys**: Uses `key(pageIndex)` to ensure Compose recycles nodes efficiently.
 * 3. **Smart Re-render**: Triggers high-quality renders only when gestures settle.
 * 4. **No Recomposition on Zoom**: Layout phase handles zoom scaling; composition phase only runs when visible pages change.
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
    // 1. Observe visible pages derived from controller state
    // Using derivedStateOf ensures we only recompose when the SET of visible pages changes
    val visiblePages by remember(controller) {
        derivedStateOf { controller.visiblePageIndices() }
    }

    // 2. Trigger Rendering
    LaunchedEffect(controller) {
        snapshotFlow {
            // Re-evaluate render needs when:
            // - Visible pages change (scroll)
            // - Gesture ends (zoom/pan finished -> high quality render)
            Triple(controller.visiblePageIndices(), state.isGestureActive, state.zoom)
        }.collectLatest { (visible, gestureActive, _) ->
            // Always request render updates for visibility changes.
            // When gesture ends (!gestureActive), this ensures we get a final sharp render.
            controller.requestRenderForVisiblePages()
        }
    }

    // 3. Prepare expensive objects (ColorFilter) once
    val colorFilter = remember(config.isNightModeEnabled) {
        if (config.isNightModeEnabled) {
            ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            ))
        } else null
    }

    // 4. Custom Layout
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
            // Emit only visible pages
            for (index in visiblePages) {
                // Key is crucial for efficient recycling of Composable nodes
                key(index) {
                    val size = pageSizes.getOrNull(index)
                    if (size != null) {
                        PdfPage(
                            bitmap = renderedPages[index],
                            pageIndex = index,
                            aspectRatio = size.width.toFloat() / size.height.toFloat(),
                            isLoading = renderedPages[index] == null,
                            showLoadingIndicator = config.isLoadingIndicatorVisible,
                            fitMode = config.fitMode,
                            colorFilter = colorFilter,
                            // Identify this child by its page index for the MeasurePolicy
                            modifier = Modifier.layoutId(index).fillMaxSize()
                        )
                    }
                }
            }
        }
    ) { measurables, constraints ->
        // MEASURE PHASE
        // This runs on every frame during gestures (zoom/pan) because state.zoom/pan are read here.
        // It's highly optimized: no recomposition, just measurement and placement.

        val vpWidth = controller.viewportWidth

        // Fallback if viewport not yet measured (first frame)
        val layoutWidth = if (vpWidth > 0f) vpWidth.roundToInt() else constraints.maxWidth
        val layoutHeight = constraints.maxHeight

        if (measurables.isEmpty() || vpWidth <= 0f) {
            return@Layout layout(layoutWidth, layoutHeight) {}
        }

        val currentZoom = state.zoom
        val panX = state.panX
        val panY = state.panY

        layout(layoutWidth, layoutHeight) {
            measurables.forEach { measurable ->
                // Identify which page this measurable belongs to
                val pageIndex = measurable.layoutId as? Int ?: return@forEach

                // Determine precise layout geometry using controller's cached calculations
                val docTopY = controller.pageTopDocY(pageIndex)
                val docHeight = controller.pageHeightPx(pageIndex)

                // Convert document coordinates to screen pixels
                // screenY = (docY * zoom) + panY
                val screenW = (vpWidth * currentZoom).roundToInt().coerceAtLeast(1)
                val screenH = (docHeight * currentZoom).roundToInt().coerceAtLeast(1)

                val x = panX.roundToInt()
                val y = (docTopY * currentZoom + panY).roundToInt()

                // Measure and Place
                // We dictate the exact size (Constraint.fixed) so child fills it
                val placeable = measurable.measure(Constraints.fixed(screenW, screenH))
                placeable.place(x, y)
            }
        }
    }
}

