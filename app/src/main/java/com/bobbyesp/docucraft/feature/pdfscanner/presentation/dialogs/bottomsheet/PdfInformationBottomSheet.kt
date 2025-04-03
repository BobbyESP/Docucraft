package com.bobbyesp.docucraft.feature.pdfscanner.presentation.dialogs.bottomsheet

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfInformationBottomSheet(
    modifier: Modifier = Modifier,
    scannedPdf: ScannedPdf,
    onDismissRequest: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        modifier = modifier,
        sheetState = sheetState,
        onDismissRequest = onDismissRequest,
    ) {
        PdfInformationBottomSheetContent(
            scannedPdf = scannedPdf,
            onDismissRequest = onDismissRequest,
            onDeleteClick = {
                // Handle delete action
            },
            onRenameClick = {
                // Handle rename action
            },
        )
    }
}

@Composable
fun PdfInformationBottomSheetContent(
    scannedPdf: ScannedPdf,
    onDismissRequest: () -> Unit,
    onDeleteClick: () -> Unit,
    onRenameClick: () -> Unit
) {

}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BottomSheetPrev() {
    DocucraftTheme {
        Box(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        ) {
            PdfInformationBottomSheet(
                scannedPdf = ScannedPdf(
                    id = "1",
                    filename = "document1.pdf",
                    title = "Documento 1 de prueba. Título corto",
                    description = "Description para el documento 1. La descripción no va a ser muy larga.",
                    path = "content://com.example.documents/document/1".toUri(),
                    createdTimestamp = System.currentTimeMillis(),
                    fileSize = 1024,
                    pageCount = 10,
                    thumbnail = "content://com.example.thumbnails/thumbnail/1"
                ),
                onDismissRequest = {},
            )
        }
    }
}