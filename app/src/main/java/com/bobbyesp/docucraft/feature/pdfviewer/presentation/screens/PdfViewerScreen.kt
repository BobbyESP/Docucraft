/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.docucraft.core.domain.model.ViewerDisplaySettings
import com.bobbyesp.docucraft.core.domain.model.ViewerFitMode
import com.bobbyesp.docucraft.core.domain.notifications.InAppNotification
import com.bobbyesp.docucraft.core.presentation.common.LocalNotificationsService
import com.bobbyesp.docucraft.core.util.events.UiEvent
import com.bobbyesp.docucraft.feature.pdfviewer.domain.actions.DocumentPrinter
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.PdfViewerViewModel
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.toolbar.PdfViewerBottomToolbar
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.toolbar.PdfViewerTopBar
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.PdfViewerEffect
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.PdfViewerIntent
import com.bobbyesp.docucraft.feature.shared.domain.BasicDocument
import com.composepdf.FitMode
import com.composepdf.PdfLayoutSpec
import com.composepdf.PdfSource
import com.composepdf.PdfViewer
import com.composepdf.PdfViewerDefaults
import com.composepdf.PdfZoomSpec
import com.composepdf.ScrollDirection
import com.composepdf.rememberPdfViewerState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    viewModel: PdfViewerViewModel,
    documentInfo: BasicDocument,
    onBack: () -> Unit,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Callers only show the screen once these are known; the factory values are a formality.
    val display = state.display ?: ViewerDisplaySettings.Factory
    val pdfViewerState = rememberPdfViewerState()

    HandlePdfViewerEffects(viewModel)

    var areControlsVisible by remember { mutableStateOf(true) }
    var isTopBarVisible by remember { mutableStateOf(true) }

    LaunchedEffect(pdfViewerState) {
        snapshotFlow { pdfViewerState.panY to pdfViewerState.isGestureActive }
            .distinctUntilChanged()
            .collect { (_, gestureActive) ->
                if (gestureActive && pdfViewerState.isLoaded) isTopBarVisible = false
            }
    }

    // The bars' own measured heights, kept while they are hidden. Handed to the viewer as constant
    // content padding: pages start below the top bar and end above the bottom one, and scroll
    // underneath them. It used to be an animated padding on the viewer itself, which resized the
    // viewport, and relaid the document out, on every frame the bars moved (V9).
    val density = LocalDensity.current
    var topBarHeight by remember { mutableStateOf(0.dp) }
    var bottomBarHeight by remember { mutableStateOf(0.dp) }

    Box(modifier = modifier.fillMaxSize()) {
        PdfViewer(
            source = PdfSource.Uri(documentInfo.uri.toUri()),
            state = pdfViewerState,
            layout =
                PdfLayoutSpec(
                    scrollDirection = ScrollDirection.VERTICAL,
                    fitMode = display.fitMode.toEngine(),
                    contentPadding = PaddingValues(top = topBarHeight, bottom = bottomBarHeight),
                ),
            zoomSpec = PdfZoomSpec(minZoom = 0.25f, maxZoom = 10f),
            style = PdfViewerDefaults.style(nightMode = display.nightMode),
            loadingContent = { LoadingIndicator(modifier = Modifier.align(Alignment.Center)) },
            onTap = {
                when {
                    // Top bar + controls both visible → hide both
                    isTopBarVisible && areControlsVisible -> {
                        isTopBarVisible = false
                        areControlsVisible = false
                    }
                    // Only controls visible → hide controls
                    !isTopBarVisible && areControlsVisible -> {
                        areControlsVisible = false
                    }
                    // Everything hidden → show both
                    else -> {
                        isTopBarVisible = true
                        areControlsVisible = true
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            modifier =
                Modifier.align(Alignment.TopCenter).onSizeChanged {
                    if (it.height > 0) topBarHeight = with(density) { it.height.toDp() }
                },
            visible = isTopBarVisible,
            enter =
                fadeIn(animationSpec = MaterialTheme.motionScheme.slowEffectsSpec()) +
                    slideInVertically(
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        initialOffsetY = { -it },
                    ),
            exit =
                fadeOut(animationSpec = MaterialTheme.motionScheme.slowEffectsSpec()) +
                    slideOutVertically(
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        targetOffsetY = { -it },
                    ),
        ) {
            PdfViewerTopBar(
                documentInfo = documentInfo,
                pageCount = pdfViewerState.pageCount,
                showBackButton = showBackButton,
                onBack = onBack,
                onShare =
                    if (state.canHandOff) ({ viewModel.onSendIntent(PdfViewerIntent.Share) })
                    else null,
                onPrint = { viewModel.onSendIntent(PdfViewerIntent.Print) },
                onOpenWith =
                    if (state.canHandOff) ({ viewModel.onSendIntent(PdfViewerIntent.OpenWith) })
                    else null,
                onDetails = onOpenDetails,
            )
        }

        AnimatedVisibility(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .onSizeChanged {
                        if (it.height > 0) bottomBarHeight = with(density) { it.height.toDp() }
                    }
                    .padding(
                        bottom =
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                16.dp
                    ),
            visible = areControlsVisible,
            enter =
                fadeIn(animationSpec = MaterialTheme.motionScheme.slowEffectsSpec()) +
                    slideInVertically(
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        initialOffsetY = { it },
                    ),
            exit =
                fadeOut(animationSpec = MaterialTheme.motionScheme.slowEffectsSpec()) +
                    slideOutVertically(
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        targetOffsetY = { it },
                    ),
        ) {
            PdfViewerBottomToolbar(
                state = pdfViewerState,
                isNightModeEnabled = display.nightMode,
                fitMode = display.fitMode.toEngine(),
                onFitModeChange = {
                    viewModel.onSendIntent(PdfViewerIntent.SetFitMode(it.toViewerFitMode()))
                },
                onNightModeToggle = { viewModel.onSendIntent(PdfViewerIntent.ToggleNightMode) },
            )
        }
    }
}

/** Carries out what the ViewModel cannot, for want of an activity, and shows what it has to say. */
@Composable
private fun HandlePdfViewerEffects(viewModel: PdfViewerViewModel) {
    val activity = requireNotNull(LocalActivity.current) { "The PDF viewer needs an activity" }
    val printer: DocumentPrinter = koinInject { parametersOf(activity) }
    val notifications = LocalNotificationsService.current

    LaunchedEffect(viewModel, printer) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is PdfViewerEffect.Print -> printer.print(effect.document, effect.jobName)
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.defaultEvents.collectLatest { event ->
            when (event) {
                is UiEvent.ShowMessage ->
                    notifications.show(
                        InAppNotification(message = event.message, type = event.type)
                    )
            }
        }
    }
}

private fun ViewerFitMode.toEngine(): FitMode =
    when (this) {
        ViewerFitMode.WIDTH -> FitMode.WIDTH
        ViewerFitMode.HEIGHT -> FitMode.HEIGHT
        ViewerFitMode.BOTH -> FitMode.BOTH
        ViewerFitMode.PROPORTIONAL -> FitMode.PROPORTIONAL
    }

private fun FitMode.toViewerFitMode(): ViewerFitMode =
    when (this) {
        FitMode.WIDTH -> ViewerFitMode.WIDTH
        FitMode.HEIGHT -> ViewerFitMode.HEIGHT
        FitMode.BOTH -> ViewerFitMode.BOTH
        FitMode.PROPORTIONAL -> ViewerFitMode.PROPORTIONAL
    }
