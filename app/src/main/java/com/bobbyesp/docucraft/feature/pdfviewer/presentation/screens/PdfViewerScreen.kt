package com.bobbyesp.docucraft.feature.pdfviewer.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import com.bhuvaneshw.pdf.compose.PdfSource
import com.bhuvaneshw.pdf.compose.rememberPdfState
import com.bhuvaneshw.pdf.compose.ui.PdfScrollBar
import com.bhuvaneshw.pdf.compose.ui.PdfViewer
import com.bhuvaneshw.pdf.compose.ui.PdfViewerContainer
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.toolbar.DocumentToolbar
import com.bobbyesp.docucraft.feature.shared.domain.BasicDocument
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalComposeUiApi::class)
@Composable
fun PdfViewerScreen(
    documentInfo: BasicDocument,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pdfState = rememberPdfState(source = PdfSource.ContentUri(documentInfo.uri))
    var areControlsVisible by remember { mutableStateOf(true) }

    // State for gesture detection (Tap vs Scroll)
    var startX by remember { mutableFloatStateOf(0f) }
    var startY by remember { mutableFloatStateOf(0f) }
    val touchSlop = 20f // Threshold to distinguish tap from scroll

    // Auto-hide controls after inactivity
    LaunchedEffect(areControlsVisible) {
        if (areControlsVisible) {
            delay(3000)
            areControlsVisible = false
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PdfViewerContainer(
                modifier = Modifier.fillMaxSize(),
                pdfState = pdfState,
                pdfScrollBar = { parentSize ->
                    val handleColor = MaterialTheme.colorScheme.secondary
                    val contentColor = contentColorFor(handleColor)

                    PdfScrollBar(
                        modifier = Modifier,
                        parentSize = parentSize,
                        handleColor = handleColor,
                        contentColor = contentColor
                    )
                },
                pdfViewer = {
                    PdfViewer(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        // Intercept events at Initial pass before the child AndroidView consumes them.
                                        // This allows us to detect gestures without blocking the PDF viewer's native touch handling.
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull() ?: continue

                                        if (change.changedToDown()) {
                                            startX = change.position.x
                                            startY = change.position.y
                                        } else if (change.changedToUp()) {
                                            val endX = change.position.x
                                            val endY = change.position.y
                                            val distanceX = kotlin.math.abs(endX - startX)
                                            val distanceY = kotlin.math.abs(endY - startY)

                                            // Toggle controls only if movement is within the tap threshold
                                            if (distanceX < touchSlop && distanceY < touchSlop) {
                                                areControlsVisible = !areControlsVisible
                                            }
                                        }
                                        // Non-consuming: events continue to flow to the native PdfViewer
                                    }
                                }
                            },
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        onCreateViewer = {
                            // PdfViewer scope
                        }
                    )
                }
            )

            AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(1f),
                visible = areControlsVisible,
                enter = fadeIn(
                    animationSpec = MaterialTheme.motionScheme.slowEffectsSpec()
                ) + slideInVertically(
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                ),
                exit = fadeOut(
                    animationSpec = MaterialTheme.motionScheme.slowEffectsSpec()
                ) + slideOutVertically(
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                )
            ) {
                DocumentToolbar(
                    pdfState = pdfState,
                    title = documentInfo.title ?: documentInfo.filename,
                    description = documentInfo.description
                        ?: stringResource(R.string.no_description),
                    onBack = onBack,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        ),
                    windowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout)
                )
            }
        }
    }
}
