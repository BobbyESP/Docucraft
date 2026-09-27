/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.data.actions

import android.app.Activity
import android.print.PrintManager
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.bobbyesp.docucraft.feature.pdfviewer.domain.actions.DocumentPrinter
import com.bobbyesp.scanner.ContentRef

/**
 * Prints through [PrintManager], which only prints from an activity: hence [activity] rather than a
 * context, and a new instance per screen rather than a singleton.
 */
class AndroidDocumentPrinter(private val activity: Activity) : DocumentPrinter {

    override fun print(document: ContentRef, jobName: String) {
        val printManager = activity.getSystemService<PrintManager>() ?: return
        // Printing reads the source directly, so a file:// location is fine here.
        val adapter = PdfPrintDocumentAdapter(activity, document.value.toUri(), jobName)
        printManager.print(jobName, adapter, null)
    }
}
