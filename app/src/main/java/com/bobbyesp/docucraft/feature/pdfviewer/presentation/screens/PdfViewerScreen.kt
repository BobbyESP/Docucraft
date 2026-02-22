package com.bobbyesp.docucraft.feature.pdfviewer.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bhuvaneshw.pdf.compose.PdfSource
import com.bhuvaneshw.pdf.compose.PdfViewer
import com.bhuvaneshw.pdf.compose.rememberPdfState
import com.bhuvaneshw.pdf.compose.ui.PdfScrollBar
import com.bhuvaneshw.pdf.compose.ui.PdfToolBar
import com.bhuvaneshw.pdf.compose.ui.PdfViewer
import com.bhuvaneshw.pdf.compose.ui.PdfViewerContainer
import com.bobbyesp.docucraft.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PdfViewerScreen(source: PdfSource, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val pdfState = rememberPdfState(source = source)

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        PdfViewerContainer(
            modifier = Modifier.padding(paddingValues),
            pdfState = pdfState,
            loadingIndicator = null,
            pdfToolBar = {
                PdfToolBar(
                    modifier = Modifier,
                    title = "[PLACEHOLDER]",
                    showEditor = true,
                    onBack = onBack,
                    contentColor = MaterialTheme.colorScheme.contentColorFor(MaterialTheme.colorScheme.surfaceContainerLow)
                )
            },
            pdfScrollBar = { parentSize ->
                PdfScrollBar(parentSize = parentSize)
            },
            pdfViewer = {
                PdfViewer()
            },
        )

    }
}