package com.composepdf.ui

import android.graphics.Bitmap
import android.util.Size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import com.composepdf.gesture.pdfGestures
import com.composepdf.state.PdfViewerState
import com.composepdf.state.ViewerConfig
import com.composepdf.state.ViewerGestureController
import com.composepdf.state.ViewerLayoutController
import kotlin.math.roundToInt

/**
 * Core layout for the PDF viewer.
 *
 * Uses a custom [Layout] to position pages absolutely based on the current viewport/layout
 * contract. This ensures pixel-perfect positioning and efficient culling of non-visible pages.
 *
 * Each visible page is rendered by a [PdfPage] composable that layers:
 * 1. A low-resolution base [ImageBitmap] (always present as a fallback).
 * 2. High-resolution tiles composited on top at the correct scale.
 *
 * ## Render dispatch responsibility
 *
 * This composable does NOT dispatch render requests on its own. Rendering remains centralized in
 * the controller contracts passed in from the viewer host, so gesture-driven and programmatic
 * renders share the same pipeline.
 *
 * @param pageSizes List of pre-calculated page dimensions at zoom 1.0.
 * @param renderedPages Map of page indices to their low-resolution base bitmaps.
 * @param state The current UI state (zoom, pan, tiles).
 * @param layoutController Layout-facing contract with viewport reads and layout-triggered mutations.
 * @param gestureController Gesture-oriented contract used by the modifier.
 * @param config Viewer configuration (night mode, indicators, etc.).
 * @param modifier Layout modifier.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Suppress("UnstableCollections")
@Composable
internal fun PdfLayout(
    pageSizes: List<Size>,
    renderedPages: Map<Int, Bitmap>,
    state: PdfViewerState,
    layoutController: ViewerLayoutController,
    gestureController: ViewerGestureController,
    config: ViewerConfig,
    modifier: Modifier = Modifier
) {
    // Recompose only when the set of visible page indices actually changes.
    val visiblePages by remember(layoutController) {
        derivedStateOf { layoutController.visiblePageIndices() }
    }

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
                layoutController.onViewportSizeChanged(size.width.toFloat(), size.height.toFloat())
            }
            .pdfGestures(
                state = state,
                controller = gestureController,
                config = config,
                zoomAnimationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                enabled = state.isLoaded
            ),
        content = {
            for (index in visiblePages) {
                key(index) {
                    val size = pageSizes.getOrNull(index)
                    if (size != null) {
                        // Convert Bitmap → ImageBitmap here (stable Compose type) so PdfPage
                        // receives a skippable parameter and avoids unnecessary recomposition.
                        val rawBitmap = renderedPages[index]
                        val imageBitmap = remember(rawBitmap) { rawBitmap?.asImageBitmap() }
                        PdfPage(
                            state = state,
                            bitmap = imageBitmap,
                            pageIndex = index,
                            showLoadingIndicator = config.isLoadingIndicatorVisible,
                            colorFilter = colorFilter,
                            modifier = Modifier.layoutId(index)
                        )
                    }
                }
            }
        }
    ) { measurables, constraints ->
        val vpWidth = layoutController.viewportWidth
        if (measurables.isEmpty() || vpWidth <= 0f) {
            return@Layout layout(constraints.maxWidth, constraints.maxHeight) {}
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            // maxPageWidth is the width of the document corridor.
            val maxPageWidth = layoutController.pageSizes.indices
                .maxOfOrNull(layoutController::pageWidthPx)
                ?: vpWidth

            measurables.forEach { measurable ->
                val pageIndex = measurable.layoutId as? Int ?: return@forEach

                val docTopY = layoutController.pageTopDocY(pageIndex)
                val pageWidth = layoutController.pageWidthPx(pageIndex)
                val pageHeight = layoutController.pageHeightPx(pageIndex)

                val screenW = (pageWidth * state.zoom).roundToInt().coerceAtLeast(1)
                val screenH = (pageHeight * state.zoom).roundToInt().coerceAtLeast(1)

                // Each page is horizontally centered within the maxPageWidth corridor.
                // panX points to the left edge of that corridor in screen space.
                val x = (state.panX + (maxPageWidth - pageWidth) * state.zoom / 2f).roundToInt()
                val y = (docTopY * state.zoom + state.panY).roundToInt()

                measurable.measure(Constraints.fixed(screenW, screenH)).place(x, y)
            }
        }
    }
}
