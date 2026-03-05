package com.bobbyesp.docucraft.feature.pdfviewer.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.toolbar.PdfViewerBottomToolbar
import com.bobbyesp.docucraft.feature.shared.domain.BasicDocument
import com.composepdf.PdfViewer
import com.composepdf.rememberPdfViewerState
import com.composepdf.source.PdfSource
import com.composepdf.state.FitMode
import com.composepdf.state.ViewerConfig
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalComposeUiApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun PdfViewerScreen(
    documentInfo: BasicDocument,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pdfViewerState = rememberPdfViewerState()

    var areControlsVisible by remember { mutableStateOf(true) }
    var isTopBarVisible by remember { mutableStateOf(true) }
    var hasScrolled by remember { mutableStateOf(false) }

    // Viewer adjustable settings — owned here so toolbar can mutate them
    var fitMode by remember { mutableStateOf(FitMode.BOTH) }
    var isNightModeEnabled by remember { mutableStateOf(false) }

    var startX by remember { mutableFloatStateOf(0f) }
    var startY by remember { mutableFloatStateOf(0f) }
    val touchSlop = 20f

    LaunchedEffect(pdfViewerState) {
        // Observe both panY and isGestureActive together as a pair.
        // We only consider it a real user scroll when the gesture is active AND panY has
        // moved enough — this filters out automatic repositioning by the viewer itself
        // (e.g. centering a short document vertically on load or after zoom).
        snapshotFlow { pdfViewerState.panY to pdfViewerState.isGestureActive }
            .distinctUntilChanged()
            .collect { (_, gestureActive) ->
                if (gestureActive && pdfViewerState.isLoaded) {
                    hasScrolled = true
                    isTopBarVisible = false
                }
            }
    }

    val overlayBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)

    val topInsetDp = WindowInsets.statusBars
        .union(WindowInsets.displayCutout)
        .asPaddingValues()
        .calculateTopPadding()
    val topAppBarHeight = TopAppBarDefaults.TopAppBarExpandedHeight + topInsetDp

    val pdfTopPadding by animateDpAsState(
        targetValue = if (isTopBarVisible && !hasScrolled) topAppBarHeight else 0.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "pdfTopPadding"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        PdfViewer(
            source = PdfSource.Uri(documentInfo.uri.toUri()),
            state = pdfViewerState,
            config = ViewerConfig(
                fitMode = fitMode,
                isNightModeEnabled = isNightModeEnabled,
                minZoom = 0.25f,
                maxZoom = 10f
            ),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = pdfTopPadding.coerceIn(minimumValue = 0.dp, maximumValue = null))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull() ?: continue

                            if (change.changedToDown()) {
                                startX = change.position.x
                                startY = change.position.y
                            } else if (change.changedToUp()) {
                                val dx = kotlin.math.abs(change.position.x - startX)
                                val dy = kotlin.math.abs(change.position.y - startY)

                                if (dx < touchSlop && dy < touchSlop) {
                                    when {
                                        isTopBarVisible && areControlsVisible -> {
                                            isTopBarVisible = false
                                            areControlsVisible = false
                                        }

                                        !isTopBarVisible && areControlsVisible -> {
                                            areControlsVisible = false
                                        }

                                        else -> {
                                            isTopBarVisible = true
                                            areControlsVisible = true
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        )

        AnimatedVisibility(
            modifier = Modifier.align(Alignment.TopCenter),
            visible = isTopBarVisible,
            enter = fadeIn(animationSpec = MaterialTheme.motionScheme.slowEffectsSpec()) +
                    slideInVertically(
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        initialOffsetY = { -it }
                    ),
            exit = fadeOut(animationSpec = MaterialTheme.motionScheme.slowEffectsSpec()) +
                    slideOutVertically(
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        targetOffsetY = { -it }
                    )
        ) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = overlayBackground),
                windowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
                title = {
                    Column(
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = documentInfo.title ?: documentInfo.filename,
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            modifier = Modifier.alpha(0.66f),
                            text = documentInfo.description
                                ?: stringResource(R.string.no_description),
                            style = MaterialTheme.typography.bodyMediumEmphasized,
                        )
                    }
                },
                navigationIcon = {
                    FilledIconButton(
                        onClick = onBack,
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                }
            )
        }

        AnimatedVisibility(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = WindowInsets.navigationBars.asPaddingValues()
                        .calculateBottomPadding() + 16.dp
                ),
            visible = areControlsVisible,
            enter = fadeIn(animationSpec = MaterialTheme.motionScheme.slowEffectsSpec()) +
                    slideInVertically(
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        initialOffsetY = { it }
                    ),
            exit = fadeOut(animationSpec = MaterialTheme.motionScheme.slowEffectsSpec()) +
                    slideOutVertically(
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        targetOffsetY = { it }
                    )
        ) {
            PdfViewerBottomToolbar(
                state = pdfViewerState,
                isNightModeEnabled = isNightModeEnabled,
                fitMode = fitMode,
                onFitModeChange = { fitMode = it },
                onNightModeToggle = { isNightModeEnabled = !isNightModeEnabled },
            )
        }
    }
}
