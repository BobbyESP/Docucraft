package com.bobbyesp.docucraft.feature.pdfscanner.presentation.dialogs.bottomsheet

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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