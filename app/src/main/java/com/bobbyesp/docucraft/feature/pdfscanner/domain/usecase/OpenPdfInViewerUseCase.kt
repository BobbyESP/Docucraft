package com.bobbyesp.docucraft.feature.pdfscanner.domain.usecase

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Use case for opening a PDF in an external viewer app. Single responsibility: launch PDF viewer
 * intent.
 */
class OpenPdfInViewerUseCase(private val context: Context) {
    operator fun invoke(pdfUri: Uri) {
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(pdfUri, "application/pdf")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
        context.startActivity(intent)
    }
}
