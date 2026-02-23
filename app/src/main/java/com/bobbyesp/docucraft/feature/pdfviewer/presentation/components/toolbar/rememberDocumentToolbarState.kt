package com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.toolbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Suppress("ModifierRequired")
@Composable
fun rememberDocumentToolbarState() =
    remember { DocumentToolbarState() }