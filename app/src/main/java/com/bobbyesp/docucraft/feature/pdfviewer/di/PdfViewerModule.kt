/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.di

import android.app.Activity
import com.bobbyesp.docucraft.feature.pdfviewer.data.actions.AndroidDocumentOpener
import com.bobbyesp.docucraft.feature.pdfviewer.data.actions.AndroidDocumentPrinter
import com.bobbyesp.docucraft.feature.pdfviewer.domain.actions.DocumentOpener
import com.bobbyesp.docucraft.feature.pdfviewer.domain.actions.DocumentPrinter
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * The PDF viewer's own bindings. Sharing is not here: it is the catalogue's `DocumentSharer`, the
 * same one the document actions use.
 */
val pdfViewerModule = module {
    single<DocumentOpener> { AndroidDocumentOpener(context = androidContext()) }

    // Printing needs the screen's activity, so it is built per caller: `parametersOf(activity)`.
    factory<DocumentPrinter> { (activity: Activity) -> AndroidDocumentPrinter(activity) }
}
