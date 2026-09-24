/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.PdfDetailsSheet
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
    var hasScrolled by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }

    LaunchedEffect(pdfViewerState) {
        snapshotFlow { pdfViewerState.panY to pdfViewerState.isGestureActive }
            .distinctUntilChanged()
            .collect { (_, gestureActive) ->
                if (gestureActive && pdfViewerState.isLoaded) {
                    hasScrolled = true
                    isTopBarVisible = false
                }
            }
    }

    val topInsetDp =
        WindowInsets.statusBars
            .union(WindowInsets.displayCutout)
            .asPaddingValues()
            .calculateTopPadding()
    // Floating pill bar ≈ expanded app-bar height plus its top/bottom floating margins.
    val topAppBarHeight = TopAppBarDefaults.TopAppBarExpandedHeight + topInsetDp + 16.dp

    val pdfTopPadding by
        animateDpAsState(
            targetValue = if (isTopBarVisible && !hasScrolled) topAppBarHeight else 0.dp,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "pdfTopPadding",
        )

    Box(modifier = modifier.fillMaxSize()) {
        PdfViewer(
            source = PdfSource.Uri(documentInfo.uri.toUri()),
            state = pdfViewerState,
            layout =
                PdfLayoutSpec(
                    scrollDirection = ScrollDirection.VERTICAL,
                    fitMode = display.fitMode.toEngine(),
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
            modifier =
                Modifier.fillMaxSize()
                    .padding(
                        top = pdfTopPadding.coerceIn(minimumValue = 0.dp, maximumValue = null)
                    ),
        )

        AnimatedVisibility(
            modifier = Modifier.align(Alignment.TopCenter),
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
                onDetails = { showDetails = true },
            )
        }

        AnimatedVisibility(
            modifier =
                Modifier.align(Alignment.BottomCenter)
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

    if (showDetails) {
        PdfDetailsSheet(
            documentInfo = documentInfo,
            pageCount = pdfViewerState.pageCount,
            onDismiss = { showDetails = false },
        )
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
