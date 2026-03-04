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

@Composable
internal fun PdfLayout(
    pageSizes: List<Size>,
    renderedPages: Map<Int, Bitmap>,
    state: PdfViewerState,
    controller: PdfViewerController,
    config: ViewerConfig,
    modifier: Modifier = Modifier
) {
    val visiblePages by remember(controller) {
        derivedStateOf { controller.visiblePageIndices() }
    }

    LaunchedEffect(controller) {
        snapshotFlow {
            Triple(controller.visiblePageIndices(), state.isGestureActive, state.zoom)
        }.collectLatest { (visible, gestureActive, zoom) ->
            controller.requestRenderForVisiblePages()
        }
    }

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
                            bitmap = renderedPages[index],
                            pageIndex = index,
                            aspectRatio = size.width.toFloat() / size.height.toFloat(),
                            isLoading = renderedPages[index] == null,
                            showLoadingIndicator = config.isLoadingIndicatorVisible,
                            currentZoom = state.zoom, // Pass current zoom for tile scaling
                            fitMode = config.fitMode,
                            colorFilter = colorFilter,
                            tiles = state.renderedTiles,
                            modifier = Modifier.layoutId(index).fillMaxSize()
                        )
                    }
                }
            }
        }
    ) { measurables, constraints ->
        val vpWidth = controller.viewportWidth
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
                val pageIndex = measurable.layoutId as? Int ?: return@forEach
                val docTopY = controller.pageTopDocY(pageIndex)
                val docHeight = controller.pageHeightPx(pageIndex)

                val screenW = (vpWidth * currentZoom).roundToInt().coerceAtLeast(1)
                val screenH = (docHeight * currentZoom).roundToInt().coerceAtLeast(1)

                val x = panX.roundToInt()
                val y = (docTopY * currentZoom + panY).roundToInt()

                val placeable = measurable.measure(Constraints.fixed(screenW, screenH))
                placeable.place(x, y)
            }
        }
    }
}
