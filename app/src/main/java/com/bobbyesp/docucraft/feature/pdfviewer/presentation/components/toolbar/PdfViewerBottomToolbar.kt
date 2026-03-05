package com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.toolbar

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.composepdf.state.FitMode
import com.composepdf.state.PdfViewerState
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Bottom floating toolbar for the PDF viewer.
 *
 * Provides:
 * - Page navigation (previous / editable page field / next)
 * - Zoom controls (zoom out / percentage tap-to-reset / zoom in)
 * - Fit mode cycling button (WIDTH → HEIGHT → BOTH → PROPORTIONAL)
 * - Night mode toggle
 *
 * Designed following Material 3 Expressive guidelines.
 *
 * @param state         The hoisted [PdfViewerState].
 * @param isNightModeEnabled Whether night mode is currently active.
 * @param fitMode       The currently active [FitMode].
 * @param onNightModeToggle Callback to toggle night mode.
 * @param modifier      Optional modifier for the toolbar container.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun PdfViewerBottomToolbar(
    state: PdfViewerState,
    isNightModeEnabled: Boolean,
    fitMode: FitMode,
    onFitModeChange: (FitMode) -> Unit,
    onNightModeToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = modifier.windowInsetsPadding(WindowInsets.ime)) {
        val metrics = remember(maxWidth) { toolbarMetrics(maxWidth) }

        Surface(
            modifier = Modifier
                .height(metrics.containerHeight)
                .animateContentSize(),
            shape = MaterialTheme.shapes.extraLargeIncreased,
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = metrics.containerHorizontalPadding,
                    vertical = metrics.containerVerticalPadding
                ),
                horizontalArrangement = Arrangement.spacedBy(metrics.itemSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { state.scrollToPage(state.currentPage - 1) },
                    enabled = state.currentPage > 0,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(metrics.iconButtonSize)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = stringResource(R.string.previous_page)
                    )
                }

                PageInputField(
                    currentPage = state.currentPage + 1,
                    pageCount = state.pageCount,
                    showPageTotal = metrics.showPageTotal,
                    pageFieldWidth = metrics.pageFieldWidth,
                    pageFieldHeight = metrics.pageFieldHeight,
                    onGoToPage = { page -> state.scrollToPage(page - 1) }
                )

                IconButton(
                    onClick = { state.scrollToPage(state.currentPage + 1) },
                    enabled = state.currentPage < state.pageCount - 1,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(metrics.iconButtonSize)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.next_page)
                    )
                }

                ToolbarDivider(metrics.dividerHeight, metrics.dividerHorizontalPadding)

                IconButton(
                    onClick = { state.zoomOut(0.25f) },
                    enabled = state.zoom > state.minZoom,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(metrics.iconButtonSize)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ZoomOut,
                        contentDescription = stringResource(R.string.zoom_out)
                    )
                }

                ZoomResetButton(
                    zoomPercent = "${(state.zoom * 100).toInt()}%",
                    minWidth = metrics.zoomResetMinWidth,
                    height = metrics.zoomResetHeight,
                    horizontalPadding = metrics.zoomResetHorizontalPadding,
                    onClick = { scope.launch { state.animateZoomTo(1f) } },
                    onSlideLeft = { state.zoomOut(0.08f) },
                    onSlideRight = { state.zoomIn(0.08f) },
                )

                IconButton(
                    onClick = { state.zoomIn(0.25f) },
                    enabled = state.zoom < state.maxZoom,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(metrics.iconButtonSize)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ZoomIn,
                        contentDescription = stringResource(R.string.zoom_in)
                    )
                }

                ToolbarDivider(metrics.dividerHeight, metrics.dividerHorizontalPadding)

                IconButton(
                    onClick = { onFitModeChange(fitMode.next()) },
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(metrics.iconButtonSize)
                ) {
                    Icon(
                        imageVector = fitMode.icon(),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        contentDescription = stringResource(R.string.fit_mode)
                    )
                }

                FilledIconToggleButton(
                    checked = isNightModeEnabled,
                    onCheckedChange = { onNightModeToggle() },
                    modifier = Modifier.size(metrics.iconButtonSize)
                ) {
                    Icon(
                        imageVector = if (isNightModeEnabled) Icons.Rounded.DarkMode
                        else Icons.Rounded.LightMode,
                        contentDescription = stringResource(R.string.night_mode)
                    )
                }
            }
        }
    }
}

// ── Private sub-components ────────────────────────────────────────────────────

@Composable
private fun PageInputField(
    currentPage: Int,
    pageCount: Int,
    showPageTotal: Boolean,
    pageFieldWidth: Dp,
    pageFieldHeight: Dp,
    onGoToPage: (Int) -> Unit,
) {
    val contentColor = LocalContentColor.current
    val focusManager = LocalFocusManager.current
    val maxPage = max(pageCount, 1)
    val maxDigits = maxPage.toString().length

    var isFocused by remember { mutableStateOf(false) }
    var pageText by remember { mutableStateOf(currentPage.coerceIn(1, maxPage).toString()) }

    LaunchedEffect(currentPage, maxPage, isFocused) {
        if (!isFocused) {
            pageText = currentPage.coerceIn(1, maxPage).toString()
        }
    }

    fun commitPageChange() {
        val nextPage = (pageText.toIntOrNull() ?: currentPage).coerceIn(1, maxPage)
        pageText = nextPage.toString()
        onGoToPage(nextPage)
        focusManager.clearFocus()
    }

    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .padding(horizontal = pageFieldWidth / 10, vertical = pageFieldHeight / 14),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.height(pageFieldHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BasicTextField(
                value = pageText,
                onValueChange = { input ->
                    pageText = input.filter { it.isDigit() }.take(maxDigits)
                },
                modifier = Modifier
                    .height(pageFieldHeight)
                    .widthIn(min = pageFieldWidth, max = pageFieldWidth * 1.5f)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.onPrimaryFixed.copy(alpha = 0.45f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(horizontal = 8.dp)
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                        if (focusState.isFocused) {
                            pageText = currentPage.coerceIn(1, maxPage).toString()
                        }
                    },
                textStyle = MaterialTheme.typography.labelLarge.copy(
                    textAlign = TextAlign.Center,
                    color = contentColor
                ),
                cursorBrush = SolidColor(contentColor),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { commitPageChange() }),
                singleLine = true,
                interactionSource = remember { MutableInteractionSource() },
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.height(pageFieldHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        innerTextField()
                    }
                }
            )

            if (showPageTotal) {
                Text(
                    text = "/ $pageCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.74f)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomResetButton(
    zoomPercent: String,
    minWidth: Dp,
    height: Dp,
    horizontalPadding: Dp,
    onClick: () -> Unit,
    onSlideLeft: () -> Unit,
    onSlideRight: () -> Unit,
) {
    val density = LocalDensity.current
    val dragStepPx = remember(density) { with(density) { 18.dp.toPx() } }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .height(height)
            .clip(MaterialTheme.shapes.small)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    dragAccumulator += delta
                    while (dragAccumulator >= dragStepPx) {
                        onSlideRight()
                        dragAccumulator -= dragStepPx
                    }
                    while (dragAccumulator <= -dragStepPx) {
                        onSlideLeft()
                        dragAccumulator += dragStepPx
                    }
                },
                onDragStarted = { dragAccumulator = 0f },
                onDragStopped = { dragAccumulator = 0f },
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding)
            .widthIn(min = minWidth),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = zoomPercent,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ToolbarDivider(height: Dp, horizontalPadding: Dp) {
    VerticalDivider(
        modifier = Modifier
            .height(height)
            .padding(horizontal = horizontalPadding),
        color = LocalContentColor.current.copy(alpha = 0.24f)
    )
}

private data class ToolbarMetrics(
    val containerHeight: Dp,
    val containerHorizontalPadding: Dp,
    val containerVerticalPadding: Dp,
    val itemSpacing: Dp,
    val iconButtonSize: Dp,
    val pageFieldWidth: Dp,
    val pageFieldHeight: Dp,
    val zoomResetMinWidth: Dp,
    val zoomResetHeight: Dp,
    val zoomResetHorizontalPadding: Dp,
    val dividerHeight: Dp,
    val dividerHorizontalPadding: Dp,
    val showPageTotal: Boolean,
)

private fun toolbarMetrics(toolbarWidth: Dp): ToolbarMetrics = when {
    toolbarWidth < 420.dp -> ToolbarMetrics(
        containerHeight = 56.dp,
        containerHorizontalPadding = 8.dp,
        containerVerticalPadding = 4.dp,
        itemSpacing = 2.dp,
        iconButtonSize = 32.dp,
        pageFieldWidth = 34.dp,
        pageFieldHeight = 30.dp,
        zoomResetMinWidth = 46.dp,
        zoomResetHeight = 30.dp,
        zoomResetHorizontalPadding = 8.dp,
        dividerHeight = 18.dp,
        dividerHorizontalPadding = 0.dp,
        showPageTotal = false,
    )

    toolbarWidth < 620.dp -> ToolbarMetrics(
        containerHeight = 64.dp,
        containerHorizontalPadding = 10.dp,
        containerVerticalPadding = 6.dp,
        itemSpacing = 3.dp,
        iconButtonSize = 36.dp,
        pageFieldWidth = 38.dp,
        pageFieldHeight = 34.dp,
        zoomResetMinWidth = 54.dp,
        zoomResetHeight = 34.dp,
        zoomResetHorizontalPadding = 10.dp,
        dividerHeight = 22.dp,
        dividerHorizontalPadding = 1.dp,
        showPageTotal = true,
    )

    else -> ToolbarMetrics(
        containerHeight = 70.dp,
        containerHorizontalPadding = 12.dp,
        containerVerticalPadding = 7.dp,
        itemSpacing = 4.dp,
        iconButtonSize = 40.dp,
        pageFieldWidth = 44.dp,
        pageFieldHeight = 38.dp,
        zoomResetMinWidth = 62.dp,
        zoomResetHeight = 38.dp,
        zoomResetHorizontalPadding = 12.dp,
        dividerHeight = 24.dp,
        dividerHorizontalPadding = 2.dp,
        showPageTotal = true,
    )
}

// ── FitMode helpers ───────────────────────────────────────────────────────────

/** Cycles through fit modes: WIDTH → HEIGHT → BOTH → PROPORTIONAL → WIDTH */
private fun FitMode.next(): FitMode = when (this) {
    FitMode.WIDTH -> FitMode.HEIGHT
    FitMode.HEIGHT -> FitMode.BOTH
    FitMode.BOTH -> FitMode.PROPORTIONAL
    FitMode.PROPORTIONAL -> FitMode.WIDTH
}

@Composable
private fun FitMode.icon(): ImageVector = when (this) {
    FitMode.WIDTH -> ImageVector.vectorResource(R.drawable.fit_page_width)
    FitMode.HEIGHT -> ImageVector.vectorResource(R.drawable.fit_page_height)
    FitMode.BOTH -> Icons.Rounded.FitScreen
    FitMode.PROPORTIONAL -> ImageVector.vectorResource(R.drawable.fit_page)
}
