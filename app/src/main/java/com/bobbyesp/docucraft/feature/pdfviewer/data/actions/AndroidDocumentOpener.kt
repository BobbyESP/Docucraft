/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.data.actions

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.bobbyesp.docucraft.feature.pdfviewer.domain.actions.DocumentOpener
import com.bobbyesp.scanner.ContentRef

/** Opens through the system chooser, so the user picks the app every time. */
class AndroidDocumentOpener(private val context: Context) : DocumentOpener {

    override fun openWith(document: ContentRef) {
        val view =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(document.value.toUri(), MIME_TYPE)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

        val chooser =
            Intent.createChooser(view, null).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }

        context.startActivity(chooser)
    }

    private companion object {
        const val MIME_TYPE = "application/pdf"
    }
}
