package com.bobbyesp.docucraft.feature.pdfscanner.presentation.dialogs.bottomsheet

import com.bobbyesp.docucraft.core.util.viewModel.ViewModelCoroutineBased
import kotlinx.coroutines.CoroutineExceptionHandler

class PdfInformationBottomSheetViewModel: ViewModelCoroutineBased() {
    override val exceptionHandler: CoroutineExceptionHandler
        get() = CoroutineExceptionHandler { _, exception ->
            // Handle the exception
        }



}