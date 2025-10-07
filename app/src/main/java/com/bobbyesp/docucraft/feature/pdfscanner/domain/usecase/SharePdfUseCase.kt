package com.bobbyesp.docucraft.feature.pdfscanner.domain.usecase

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Use case for sharing a PDF via Android share sheet. Single responsibility: launch share intent.
 */
class SharePdfUseCase(private val context: Context) {
    operator fun invoke(pdfUri: Uri) {
        val shareIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, pdfUri)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

        val chooser =
            Intent.createChooser(shareIntent, null).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }

        context.startActivity(chooser)
    }
}
