/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.screens

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.bobbyesp.docucraft.core.domain.analytics.AnalyticsEvent
import com.bobbyesp.docucraft.core.presentation.common.LocalAnalyticsHelper
import com.bobbyesp.docucraft.feature.pdfviewer.domain.PdfDocumentActions
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.PdfDetailsSheet
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.toolbar.PdfViewerBottomToolbar
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.toolbar.PdfViewerTopBar
import com.bobbyesp.docucraft.feature.shared.domain.BasicDocument
import com.composepdf.FitMode
import com.composepdf.PdfLayoutSpec
import com.composepdf.PdfSource
import com.composepdf.PdfViewer
import com.composepdf.PdfViewerDefaults
import com.composepdf.PdfZoomSpec
import com.composepdf.ScrollDirection
import com.composepdf.rememberPdfViewerState
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    documentInfo: BasicDocument,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
) {
    val pdfViewerState = rememberPdfViewerState()
    val analyticsHelper = LocalAnalyticsHelper.current
    // Built from the composition's context (the host Activity) — printing requires an Activity.
    val context = LocalContext.current
    val documentActions = remember(context) { PdfDocumentActions(context) }

    var areControlsVisible by remember { mutableStateOf(true) }
    var isTopBarVisible by remember { mutableStateOf(true) }
    var hasScrolled by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }

    var fitMode by remember { mutableStateOf(FitMode.BOTH) }
    var isNightModeEnabled by remember { mutableStateOf(false) }

    val jobName = documentInfo.title ?: documentInfo.filename

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
            layout = PdfLayoutSpec(scrollDirection = ScrollDirection.VERTICAL, fitMode = fitMode),
            zoomSpec = PdfZoomSpec(minZoom = 0.25f, maxZoom = 10f),
            style = PdfViewerDefaults.style(nightMode = isNightModeEnabled),
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
                onShare = { documentActions.share(documentInfo.uri) },
                onPrint = { documentActions.print(documentInfo.uri, jobName) },
                onOpenWith = { documentActions.openWith(documentInfo.uri) },
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
                isNightModeEnabled = isNightModeEnabled,
                fitMode = fitMode,
                onFitModeChange = {
                    fitMode = it
                    analyticsHelper.logEvent(
                        AnalyticsEvent(
                            type = AnalyticsEvent.Types.PDF_VIEWER_SETTING_CHANGED,
                            extras =
                                listOf(
                                    AnalyticsEvent.Param(
                                        AnalyticsEvent.ParamKeys.SETTING_NAME,
                                        "fit_mode",
                                    ),
                                    AnalyticsEvent.Param(
                                        AnalyticsEvent.ParamKeys.SETTING_VALUE,
                                        it.name,
                                    ),
                                ),
                        )
                    )
                },
                onNightModeToggle = {
                    isNightModeEnabled = !isNightModeEnabled
                    analyticsHelper.logEvent(
                        AnalyticsEvent(
                            type = AnalyticsEvent.Types.PDF_VIEWER_SETTING_CHANGED,
                            extras =
                                listOf(
                                    AnalyticsEvent.Param(
                                        AnalyticsEvent.ParamKeys.SETTING_NAME,
                                        "night_mode",
                                    ),
                                    AnalyticsEvent.Param(
                                        AnalyticsEvent.ParamKeys.SETTING_VALUE,
                                        isNightModeEnabled.toString(),
                                    ),
                                ),
                        )
                    )
                },
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
