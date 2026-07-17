/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.ui

import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import com.composepdf.PdfViewerState
import com.composepdf.ScrollDirection
import com.composepdf.ViewerConfig
import com.composepdf.internal.engine.TileDraw
import com.composepdf.internal.logic.ViewerController
import com.composepdf.internal.ui.gesture.viewerGestures
import kotlin.math.roundToInt

/**
 * Core layout for the PDF viewer.
 *
 * A custom [Layout] positions the visible pages absolutely from the current pan/zoom; only the
 * placement block re-executes while panning or zooming. Each page is a [PdfPage] that draws the
 * base bitmap plus whatever tiles the engine has published for it — the UI layer is a passive
 * projection of engine state and never triggers rendering by itself (gestures do, through the
 * controller).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Suppress("UnstableCollections")
@Composable
internal fun PdfLayout(
    renderedPages: Map<Int, Bitmap>,
    tilesByPage: Map<Int, List<TileDraw>>,
    state: PdfViewerState,
    controller: ViewerController,
    config: ViewerConfig,
    modifier: Modifier = Modifier,
) {
    // Recompose only when the set of visible page indices actually changes.
    val visiblePages by remember(controller) { derivedStateOf { controller.visiblePageIndices() } }

    val colorFilter =
        remember(config.isNightModeEnabled) {
            if (config.isNightModeEnabled) {
                ColorFilter.colorMatrix(
                    ColorMatrix(
                        floatArrayOf(
                            -1f,
                            0f,
                            0f,
                            0f,
                            255f,
                            0f,
                            -1f,
                            0f,
                            0f,
                            255f,
                            0f,
                            0f,
                            -1f,
                            0f,
                            255f,
                            0f,
                            0f,
                            0f,
                            1f,
                            0f,
                        )
                    )
                )
            } else null
        }

    Layout(
        modifier =
            modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged { size ->
                    controller.onViewportSizeChanged(size.width.toFloat(), size.height.toFloat())
                }
                .viewerGestures(
                    state = state,
                    controller = controller,
                    config = config,
                    zoomAnimationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    enabled = state.isLoaded,
                ),
        content = {
            for (index in visiblePages) {
                key(index) {
                    val rawBitmap = renderedPages[index]
                    val imageBitmap = remember(rawBitmap) { rawBitmap?.asImageBitmap() }
                    PdfPage(
                        state = state,
                        bitmap = imageBitmap,
                        tiles = tilesByPage[index].orEmpty(),
                        showLoadingIndicator = config.isLoadingIndicatorVisible,
                        colorFilter = colorFilter,
                        modifier = Modifier.layoutId(index),
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val vpWidth = controller.viewportWidth
        val vpHeight = controller.viewportHeight
        if (measurables.isEmpty() || vpWidth <= 0f || vpHeight <= 0f) {
            return@Layout layout(constraints.maxWidth, constraints.maxHeight) {}
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            val corridorBreadth = controller.corridorBreadth()

            measurables.forEach { measurable ->
                val pageIndex = measurable.layoutId as? Int ?: return@forEach

                val pageWidth = controller.pageWidthPx(pageIndex)
                val pageHeight = controller.pageHeightPx(pageIndex)

                val screenW = (pageWidth * state.zoom).roundToInt().coerceAtLeast(1)
                val screenH = (pageHeight * state.zoom).roundToInt().coerceAtLeast(1)

                val x: Int
                val y: Int

                if (config.scrollDirection == ScrollDirection.VERTICAL) {
                    x = (state.panX + (corridorBreadth - pageWidth) * state.zoom / 2f).roundToInt()
                    y = (controller.pageTopDocY(pageIndex) * state.zoom + state.panY).roundToInt()
                } else {
                    x = (controller.pageLeftDocX(pageIndex) * state.zoom + state.panX).roundToInt()
                    y = (state.panY + (corridorBreadth - pageHeight) * state.zoom / 2f).roundToInt()
                }

                measurable.measure(Constraints.fixed(screenW, screenH)).place(x, y)
            }
        }
    }
}
