package com.bobbyesp.docucraft.feature.pdfviewer.presentation.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.core.net.toUri
import com.bobbyesp.docucraft.feature.shared.domain.BasicDocument
import com.composepdf.rememberPdfViewerState
import com.composepdf.state.ViewerConfig
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalComposeUiApi::class)
@Composable
fun PdfViewerScreen(
    documentInfo: BasicDocument,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pdfViewerState = rememberPdfViewerState()
    var areControlsVisible by remember { mutableStateOf(true) }
    var toolbarHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    var startX by remember { mutableFloatStateOf(0f) }
    var startY by remember { mutableFloatStateOf(0f) }
    val touchSlop = 20f

    LaunchedEffect(areControlsVisible) {
        if (areControlsVisible) {
            delay(3000)
            areControlsVisible = false
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Toolbar height en Dp para el padding de la scrollbar
        val toolbarHeightDp = with(density) { toolbarHeightPx.toDp() }
        val statusBarInsets = WindowInsets.statusBars
        val statusBarTopDp = with(density) { statusBarInsets.getTop(this).toDp() }

        val scrollBarTopPadding by animateDpAsState(
            targetValue = if (areControlsVisible) toolbarHeightDp else statusBarTopDp,
            label = "scrollBarTopPadding"
        )

        com.composepdf.PdfViewer(
            source = com.composepdf.source.PdfSource.Uri(documentInfo.uri.toUri()),
            state = pdfViewerState,
            config = ViewerConfig(),
            modifier = Modifier
        )

//        PdfViewerContainer(
//            modifier = Modifier.fillMaxSize(),
//            pdfState = pdfState,
//            pdfScrollBar = { parentSize ->
//                val handleColor = MaterialTheme.colorScheme.secondary
//                val contentColor = contentColorFor(handleColor)
//
//                PdfScrollBar(
//                    modifier = Modifier.padding(top = scrollBarTopPadding),
//                    parentSize = parentSize,
//                    handleColor = handleColor,
//                    contentColor = contentColor
//                )
//            },
//            pdfViewer = {
//                PdfViewer(
//                    modifier = Modifier
//                        .fillMaxSize()
//                        .pointerInput(Unit) {
//                            awaitPointerEventScope {
//                                while (true) {
//                                    val event = awaitPointerEvent(PointerEventPass.Initial)
//                                    val change = event.changes.firstOrNull() ?: continue
//
//                                    if (change.changedToDown()) {
//                                        startX = change.position.x
//                                        startY = change.position.y
//                                    } else if (change.changedToUp()) {
//                                        val distanceX = kotlin.math.abs(change.position.x - startX)
//                                        val distanceY = kotlin.math.abs(change.position.y - startY)
//
//                                        if (distanceX < touchSlop && distanceY < touchSlop) {
//                                            areControlsVisible = !areControlsVisible
//                                        }
//                                    }
//                                }
//                            }
//                        },
//                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
//                    onCreateViewer = {}
//                )
//            }
//        )
//
//        AnimatedVisibility(
//            modifier = Modifier
//                .align(Alignment.TopCenter)
//                .zIndex(1f)
//                .onGloballyPositioned { coords ->
//                    toolbarHeightPx = coords.size.height
//                },
//            visible = areControlsVisible,
//            enter = fadeIn(animationSpec = MaterialTheme.motionScheme.slowEffectsSpec())
//                    + slideInVertically(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()),
//            exit = fadeOut(animationSpec = MaterialTheme.motionScheme.slowEffectsSpec())
//                    + slideOutVertically(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec())
//        ) {
//            DocumentToolbar(
//                pdfState = pdfState,
//                title = documentInfo.title ?: documentInfo.filename,
//                description = documentInfo.description
//                    ?: stringResource(R.string.no_description),
//                onBack = onBack,
//                modifier = Modifier.background(
//                    MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
//                ),
//                windowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout)
//            )
//        }
    }
}
