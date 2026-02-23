package com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.toolbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.bhuvaneshw.pdf.PdfEditor
import com.bhuvaneshw.pdf.PdfListener
import com.bhuvaneshw.pdf.compose.PdfState
import com.bhuvaneshw.pdf.compose.ui.PdfContainerScope
import com.bobbyesp.docucraft.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DocumentToolbar(
    pdfState: PdfState,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    toolbarState: DocumentToolbarState = rememberDocumentToolbarState()
) {
    DisposableEffect(pdfState.pdfViewer) {
        val listener = object : PdfListener {
            override fun onEditorModeStateChange(state: PdfEditor.EditorModeState) {
                toolbarState.updateIsFindBarOpen(state.isTextHighlighterOn)
                toolbarState.updateIsEditorFreeTextOn(state.isEditorFreeTextOn)
                toolbarState.updateIsEditorInkOn(state.isEditorInkOn)
                toolbarState.updateIsEditorStampOn(state.isEditorStampOn)
            }
        }
        pdfState.pdfViewer?.addListener(listener)

        onDispose {
            pdfState.pdfViewer?.removeListener(listener)
        }
    }

    TopAppBar(
        modifier = modifier,
        windowInsets = windowInsets,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        ),
        title = {
            Column(
                modifier = Modifier,
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLargeEmphasized,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    modifier = Modifier.alpha(0.66f),
                    text = description,
                    style = MaterialTheme.typography.bodyMediumEmphasized,
                )
            }
        },
        navigationIcon = {
            onBack?.let {
                FilledIconButton(
                    onClick = onBack,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    content = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                )
            }
        }
    )
}

@Composable
fun PdfContainerScope.DocumentToolbar(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    toolbarState: DocumentToolbarState = rememberDocumentToolbarState()
) {
    DocumentToolbar(
        pdfState = pdfState,
        title = title,
        description = description,
        modifier = modifier,
        onBack = onBack,
        toolbarState = toolbarState
    )
}