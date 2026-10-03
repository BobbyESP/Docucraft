/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.di

import com.bobbyesp.docucraft.feature.pdfviewer.data.content.CatalogueRecognizedTextProvider
import com.bobbyesp.docucraft.feature.pdfviewer.data.content.LayeredPageContentProvider
import com.bobbyesp.docucraft.feature.pdfviewer.data.content.PlatformPageContentProvider
import com.bobbyesp.documentcontent.PageContentProvider
import com.bobbyesp.ocr.mlkit.MlKitTextRecognitionProvider
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** The reader of a PDF's own text layer. */
const val EMBEDDED_TEXT = "embeddedText"

/** The text recognition engine, reading a page from its image. */
const val TEXT_RECOGNITION = "textRecognition"

/**
 * Where page content comes from.
 * - [EMBEDDED_TEXT] and [TEXT_RECOGNITION] are the two readers. Reading a document to search it
 *   uses them one after the other, and decides itself when recognition is wanted.
 * - The unnamed binding is what the viewer reads with: the document's own text and, where a page
 *   has none, what the catalogue knows of its recognized text.
 *
 * The recognition engine is swapped at one line: the [TEXT_RECOGNITION] binding.
 */
val pageContentModule = module {
    single<PageContentProvider>(named(EMBEDDED_TEXT)) {
        PlatformPageContentProvider(context = androidContext())
    }

    single<PageContentProvider>(named(TEXT_RECOGNITION)) {
        MlKitTextRecognitionProvider(context = androidContext())
    }

    single<PageContentProvider> {
        LayeredPageContentProvider(
            embedded = get(named(EMBEDDED_TEXT)),
            recognized =
                CatalogueRecognizedTextProvider(
                    documents = get(),
                    pages = get(),
                    recognition = get(named(TEXT_RECOGNITION)),
                ),
        )
    }
}
