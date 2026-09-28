/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.di

import android.app.Activity
import com.bobbyesp.docucraft.feature.pdfviewer.data.actions.AndroidDocumentOpener
import com.bobbyesp.docucraft.feature.pdfviewer.data.actions.AndroidDocumentPrinter
import com.bobbyesp.docucraft.feature.pdfviewer.data.details.AndroidDocumentFactsReader
import com.bobbyesp.docucraft.feature.pdfviewer.data.links.AndroidLinkOpener
import com.bobbyesp.docucraft.feature.pdfviewer.data.settings.InMemoryViewerSessionSettings
import com.bobbyesp.docucraft.feature.pdfviewer.domain.actions.DocumentOpener
import com.bobbyesp.docucraft.feature.pdfviewer.domain.actions.DocumentPrinter
import com.bobbyesp.docucraft.feature.pdfviewer.domain.details.DocumentFactsReader
import com.bobbyesp.docucraft.feature.pdfviewer.domain.details.ObserveViewerDocumentDetailsUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.domain.links.LinkOpener
import com.bobbyesp.docucraft.feature.pdfviewer.domain.links.ResolveLinkUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.domain.model.ViewerDocumentRef
import com.bobbyesp.docucraft.feature.pdfviewer.domain.settings.ViewerSessionSettings
import com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase.DetectDocumentTextUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase.ObserveViewerDisplaySettingsUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase.ObserveViewerDocumentUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase.UpdateViewerDisplaySettingsUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.PdfViewerViewModel
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.details.PdfDocumentDetailsViewModel
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.pages.ViewerPageRequests
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The PDF viewer's own bindings. Sharing is not here: it is the catalogue's `DocumentSharer`, the
 * same one the document actions use.
 */
val pdfViewerModule = module {
    single<DocumentOpener> { AndroidDocumentOpener(context = androidContext()) }

    // Links open from the screen's activity, so that a Custom Tab stays in the app's task (D4).
    factory<LinkOpener> { (activity: Activity) -> AndroidLinkOpener(activity) }
    factory { ResolveLinkUseCase() }

    // Printing needs the screen's activity, so it is built per caller: `parametersOf(activity)`.
    factory<DocumentPrinter> { (activity: Activity) -> AndroidDocumentPrinter(activity) }

    // One for the whole process: that is the session D2 remembers settings for, shared by both
    // activities that show documents.
    single<ViewerSessionSettings> { InMemoryViewerSessionSettings() }

    // Where *Go to page* leaves the page for the viewer to take: shared by both hosts.
    single { ViewerPageRequests() }

    factory { ObserveViewerDocumentUseCase(observeDocument = get()) }
    single<DocumentFactsReader> { AndroidDocumentFactsReader(context = androidContext()) }
    factory { DetectDocumentTextUseCase(provider = get()) }
    factory {
        val detectText: DetectDocumentTextUseCase = get()
        ObserveViewerDocumentDetailsUseCase(
            observeDocument = get(),
            facts = get(),
            detectText = { detectText(it) },
        )
    }
    factory { ObserveViewerDisplaySettingsUseCase(session = get(), settingsRepository = get()) }
    factory { UpdateViewerDisplaySettingsUseCase(session = get()) }

    // Scoped to whoever shows the document, so which one comes from the caller, not the graph.
    viewModel { (ref: ViewerDocumentRef) ->
        PdfViewerViewModel(
            ref = ref,
            savedStateHandle = get(),
            observeDocument = get(),
            observeDisplaySettings = get(),
            updateDisplaySettings = get(),
            documentSharer = get(),
            documentOpener = get(),
            stringProvider = get(),
            analyticsHelper = get(),
            contentProvider = get(),
            resolveLink = get(),
        )
    }

    viewModel { (ref: ViewerDocumentRef) ->
        PdfDocumentDetailsViewModel(ref = ref, observeDetails = get())
    }
}
