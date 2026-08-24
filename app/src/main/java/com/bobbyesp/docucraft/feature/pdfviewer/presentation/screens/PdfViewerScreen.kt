/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.bobbyesp.docucraft.core.domain.analytics.AnalyticsEvent
import com.bobbyesp.docucraft.core.presentation.common.LocalAnalyticsHelper
import com.bobbyesp.docucraft.feature.pdfviewer.domain.PdfDocumentActions
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.PdfDetailsSheet
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.toolbar.PdfPageScrubber
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
import kotlinx.coroutines.delay

/** How long the scrubber lingers after the document stops moving. */
private const val SCRUBBER_LINGER_MS = 1_400L

/** Below this the viewer is considered at rest, ignoring the tail of a decaying fling. */
private const val AT_REST_VELOCITY = 1f

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    documentInfo: BasicDocument,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
) {
    val state = rememberPdfViewerState()
    val analyticsHelper = LocalAnalyticsHelper.current
    // Built from the composition's context (the host Activity) — printing requires an Activity.
    val context = LocalContext.current
    val documentActions = remember(context) { PdfDocumentActions(context) }

    // One flag for all chrome. The previous three (controls / top bar / a one-way "has scrolled"
    // latch) could reach states where the bar was visible but the viewer had already given up its
    // padding, leaving the bar sitting on top of the page for the rest of the session.
    var chromeVisible by remember { mutableStateOf(true) }
    var showDetails by remember { mutableStateOf(false) }

    var fitMode by remember { mutableStateOf(FitMode.BOTH) }
    var isNightModeEnabled by remember { mutableStateOf(false) }

    val jobName = documentInfo.title ?: documentInfo.filename

    // "The document is moving" covers both the finger and the fling that follows it, so chrome
    // does not reappear halfway through a decay.
    val isMoving by remember {
        derivedStateOf {
            state.isGestureActive || state.scrollVelocity.getDistance() > AT_REST_VELOCITY
        }
    }

    // Reading gets the screen: any movement takes the chrome away.
    LaunchedEffect(isMoving) {
        if (isMoving && state.isLoaded) chromeVisible = false
    }

    // The scrubber doubles as the position readout, so it stays through a scroll and for a moment
    // after — that is exactly when "which page am I on" is worth answering.
    var scrubberLingering by remember { mutableStateOf(false) }
    LaunchedEffect(isMoving) {
        if (isMoving) {
            scrubberLingering = true
        } else {
            delay(SCRUBBER_LINGER_MS)
            scrubberLingering = false
        }
    }

    val scrubberVisible = state.isLoaded && (chromeVisible || scrubberLingering)

    Box(modifier = modifier.fillMaxSize()) {
        // Never padded. Insetting the viewer to make room for the bar resized the viewport, which
        // re-fits the page and makes the document jump every time the chrome appears or leaves.
        // Chrome floats over the page instead.
        PdfViewer(
            source = PdfSource.Uri(documentInfo.uri.toUri()),
            state = state,
            layout = PdfLayoutSpec(scrollDirection = ScrollDirection.VERTICAL, fitMode = fitMode),
            zoomSpec = PdfZoomSpec(minZoom = 0.25f, maxZoom = 10f),
            style = PdfViewerDefaults.style(nightMode = isNightModeEnabled),
            loadingContent = { LoadingIndicator(modifier = Modifier.align(Alignment.Center)) },
            onTap = { chromeVisible = !chromeVisible },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            modifier = Modifier.align(Alignment.TopCenter),
            visible = chromeVisible,
            // Fade and slide share one timing family; mixing a slow fade with a fast slide is what
            // made the bar look like it was sliding out from under its own opacity.
            enter =
                fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    slideInVertically(
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        initialOffsetY = { -it },
                    ),
            exit =
                fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    slideOutVertically(
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        targetOffsetY = { -it },
                    ),
        ) {
            PdfViewerTopBar(
                documentInfo = documentInfo,
                currentPage = state.currentPage,
                pageCount = state.pageCount,
                showBackButton = showBackButton,
                fitMode = fitMode,
                isNightModeEnabled = isNightModeEnabled,
                onBack = onBack,
                onShare = { documentActions.share(documentInfo.uri) },
                onPrint = { documentActions.print(documentInfo.uri, jobName) },
                onOpenWith = { documentActions.openWith(documentInfo.uri) },
                onDetails = { showDetails = true },
                onFitModeChange = { mode ->
                    fitMode = mode
                    analyticsHelper.logSettingChange("fit_mode", mode.name)
                },
                onNightModeToggle = {
                    isNightModeEnabled = !isNightModeEnabled
                    analyticsHelper.logSettingChange(
                        "night_mode",
                        isNightModeEnabled.toString(),
                    )
                },
            )
        }

        AnimatedVisibility(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .padding(
                        bottom =
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                16.dp,
                        start = 16.dp,
                        end = 16.dp,
                    ),
            visible = scrubberVisible,
            enter =
                fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    slideInVertically(
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        initialOffsetY = { it },
                    ),
            exit =
                fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    slideOutVertically(
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        targetOffsetY = { it },
                    ),
        ) {
            PdfPageScrubber(
                currentPage = state.currentPage,
                pageCount = state.pageCount,
                onSeekToPage = { page -> state.scrollToPage(page) },
            )
        }
    }

    if (showDetails) {
        PdfDetailsSheet(
            documentInfo = documentInfo,
            pageCount = state.pageCount,
            onDismiss = { showDetails = false },
        )
    }
}

private fun com.bobbyesp.docucraft.core.domain.repository.AnalyticsHelper.logSettingChange(
    name: String,
    value: String,
) {
    logEvent(
        AnalyticsEvent(
            type = AnalyticsEvent.Types.PDF_VIEWER_SETTING_CHANGED,
            extras =
                listOf(
                    AnalyticsEvent.Param(AnalyticsEvent.ParamKeys.SETTING_NAME, name),
                    AnalyticsEvent.Param(AnalyticsEvent.ParamKeys.SETTING_VALUE, value),
                ),
        )
    )
}
