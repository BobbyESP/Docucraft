package com.composepdf.ui

import android.graphics.Bitmap
import android.util.Size
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.composepdf.state.PdfViewerController
import com.composepdf.state.PdfViewerState
import com.composepdf.state.ScrollDirection
import com.composepdf.state.ViewerConfig
import kotlinx.coroutines.flow.collectLatest

/**
 * Lazy layout for displaying PDF pages with efficient recycling.
 * 
 * This composable handles the layout of multiple PDF pages in either
 * vertical or horizontal scrolling mode, with support for page snapping
 * and zoom transformations.
 * 
 * @param pageCount The total number of pages
 * @param pageSizes List of page dimensions
 * @param renderedPages Map of page index to rendered bitmap
 * @param state The PDF viewer state
 * @param controller The PDF viewer controller
 * @param config The viewer configuration
 * @param modifier Modifier for the layout
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PdfLayout(
    pageCount: Int,
    pageSizes: List<Size>,
    renderedPages: Map<Int, Bitmap>,
    state: PdfViewerState,
    controller: PdfViewerController,
    config: ViewerConfig,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val pageSpacingPx = with(density) { config.pageSpacing.roundToPx() }
    
    // Calculate snap fling behavior if enabled
    val flingBehavior = if (config.isPageSnappingEnabled) {
        rememberSnapFlingBehavior(lazyListState = state.lazyListState)
    } else {
        null
    }
    
    // Track scroll position changes
    LaunchedEffect(state.lazyListState) {
        snapshotFlow { state.lazyListState.firstVisibleItemIndex }
            .collectLatest { index ->
                controller.onScrollPositionChanged(index)
            }
    }
    
    // Request render when visible items change
    LaunchedEffect(state.lazyListState) {
        snapshotFlow { 
            state.lazyListState.layoutInfo.visibleItemsInfo.map { it.index }
        }.collectLatest {
            controller.requestRenderForVisiblePages()
        }
    }
    
    val contentModifier = modifier
        .fillMaxSize()
        .graphicsLayer {
            scaleX = state.zoom
            scaleY = state.zoom
            translationX = state.offset.x
            translationY = state.offset.y
        }
    
    when (config.scrollDirection) {
        ScrollDirection.VERTICAL -> {
            LazyColumn(
                state = state.lazyListState,
                modifier = contentModifier,
                contentPadding = PaddingValues(vertical = config.pageSpacing),
                verticalArrangement = Arrangement.spacedBy(config.pageSpacing),
                flingBehavior = flingBehavior ?: androidx.compose.foundation.gestures.ScrollableDefaults.flingBehavior()
            ) {
                items(
                    count = pageCount,
                    key = { index -> index }
                ) { index ->
                    val pageSize = pageSizes.getOrNull(index) ?: Size(1, 1)
                    val aspectRatio = pageSize.width.toFloat() / pageSize.height.toFloat()
                    val bitmap = renderedPages[index]
                    
                    PdfPage(
                        bitmap = bitmap,
                        pageIndex = index,
                        aspectRatio = aspectRatio,
                        isLoading = bitmap == null,
                        showLoadingIndicator = config.isLoadingIndicatorVisible
                    )
                }
            }
        }
        
        ScrollDirection.HORIZONTAL -> {
            LazyRow(
                state = state.lazyListState,
                modifier = contentModifier,
                contentPadding = PaddingValues(horizontal = config.pageSpacing),
                horizontalArrangement = Arrangement.spacedBy(config.pageSpacing),
                flingBehavior = flingBehavior ?: androidx.compose.foundation.gestures.ScrollableDefaults.flingBehavior()
            ) {
                items(
                    count = pageCount,
                    key = { index -> index }
                ) { index ->
                    val pageSize = pageSizes.getOrNull(index) ?: Size(1, 1)
                    val aspectRatio = pageSize.width.toFloat() / pageSize.height.toFloat()
                    val bitmap = renderedPages[index]
                    
                    PdfPage(
                        bitmap = bitmap,
                        pageIndex = index,
                        aspectRatio = aspectRatio,
                        isLoading = bitmap == null,
                        showLoadingIndicator = config.isLoadingIndicatorVisible
                    )
                }
            }
        }
    }
}
