package com.composepdf.ui

import android.graphics.Bitmap
import android.util.Size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import com.composepdf.gesture.pdfGestures
import com.composepdf.state.PdfViewerController
import com.composepdf.state.PdfViewerState
import com.composepdf.state.ViewerConfig
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

/**
 * Core layout for the PDF viewer.
 *
 * ## Positioning strategy
 *
 * A single [Layout] composable contains ALL visible pages as children.
 * The Layout:
 *   1. Measures every child at its exact pixel size (screenW × screenH).
 *   2. Places each child at its exact screen position (screenX, screenY).
 *   3. Reports its own size as the viewport size so the parent Box knows
 *      how much space is used.
 *
 * This is the only correct way to position children at absolute pixel positions
 * inside a Compose layout tree:
 *   - No Dp/pixel conversion errors (unlike absoluteOffset + width/height in Dp).
 *   - Placement affects BOTH drawing AND hit-testing (unlike graphicsLayer translation).
 *   - A single Layout means no inter-child rounding accumulation (unlike one Layout per page).
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
    val visibleRange by remember(state, controller) {
        derivedStateOf { controller.visiblePageIndices() }
    }

    // Request renders whenever the visible page range changes.
    // No debounce, no gesture-active guard — the controller and scheduler
    // handle deduplication internally (in-flight jobs are not relaunched).
    LaunchedEffect(controller) {
        snapshotFlow { controller.visiblePageIndices() }
            .distinctUntilChanged()
            .collectLatest { controller.requestRenderForVisiblePages() }
    }

    // Pre-compute the indices list outside the Layout lambda so it's stable.
    val visibleIndices = remember(visibleRange, pageSizes) {
        visibleRange.filter { it in pageSizes.indices }
    }

    Layout(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { size ->
                controller.onViewportSizeChanged(size.width.toFloat(), size.height.toFloat())
            }
            .pdfGestures(
                state      = state,
                controller = controller,
                config     = config,
                enabled    = state.isLoaded
            ),
        content = {
            val vpWidth = controller.viewportWidth
            if (vpWidth > 0f && pageSizes.isNotEmpty()) {
                for (index in visibleIndices) {
                    val s = pageSizes[index]
                    PdfPage(
                        bitmap               = renderedPages[index],
                        pageIndex            = index,
                        aspectRatio          = s.width.toFloat() / s.height.toFloat(),
                        isLoading            = renderedPages[index] == null,
                        showLoadingIndicator = config.isLoadingIndicatorVisible,
                        modifier             = Modifier.fillMaxSize()
                    )
                }
            }
        }
    ) { measurables, constraints ->
        val vpWidth = controller.viewportWidth

        // Viewport dimensions in px — use constraints as fallback before first onSizeChanged
        val vpW = if (vpWidth > 0f) vpWidth.roundToInt() else constraints.maxWidth
        val vpH = constraints.maxHeight

        if (vpW == 0 || pageSizes.isEmpty() || measurables.isEmpty()) {
            return@Layout layout(vpW.coerceAtLeast(1), vpH.coerceAtLeast(1)) {}
        }

        // Measure and position each page
        val placeables = measurables.mapIndexed { localIdx, measurable ->
            val pageIndex = visibleIndices.getOrElse(localIdx) { return@Layout layout(vpW, vpH) {} }

            val docHeight = controller.pageHeightPx(pageIndex)
            val docTopY   = controller.pageTopDocY(pageIndex)

            val screenW = (vpWidth  * state.zoom).roundToInt().coerceAtLeast(1)
            val screenH = (docHeight * state.zoom).roundToInt().coerceAtLeast(1)

            // panX is the left-edge offset of the page in screen pixels.
            // clampPan() ensures it is always within valid bounds:
            //   zoom = 1: panX = (vpW - scaledW) / 2  → page centred
            //   zoom > 1: panX ∈ [-(scaledW - vpW), 0] → full horizontal scroll range
            val screenX = state.panX.roundToInt()
            val screenY = (docTopY * state.zoom + state.panY).roundToInt()

            Triple(
                measurable.measure(Constraints.fixed(screenW, screenH)),
                screenX,
                screenY
            )
        }

        layout(vpW, vpH) {
            for ((placeable, x, y) in placeables) {
                placeable.place(x, y)
            }
        }
    }
}
