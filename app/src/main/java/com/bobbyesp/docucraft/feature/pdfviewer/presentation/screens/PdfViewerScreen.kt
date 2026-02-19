package com.bobbyesp.docucraft.feature.pdfviewer.presentation.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bhuvaneshw.pdf.compose.PdfSource
import com.bhuvaneshw.pdf.compose.PdfViewer
import com.bhuvaneshw.pdf.compose.rememberPdfState

@Composable
fun PdfViewerScreen(source: PdfSource, modifier: Modifier = Modifier) {
    val pdfState = rememberPdfState(source = source)

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        PdfViewer(
            modifier = Modifier.padding(paddingValues),
            pdfState = pdfState,
        )
    }
}