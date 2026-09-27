/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.di

import com.bobbyesp.docucraft.feature.pdfviewer.data.content.LayeredPageContentProvider
import com.bobbyesp.docucraft.feature.pdfviewer.data.content.PlatformPageContentProvider
import com.bobbyesp.documentcontent.PageContentProvider
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Where page content comes from: the one line that changes when text recognition arrives. It will
 * live in its own module, as the scanner does, and go in as `recognized`.
 */
val pageContentModule = module {
    single<PageContentProvider> {
        LayeredPageContentProvider(
            embedded = PlatformPageContentProvider(context = androidContext()),
            recognized = null,
        )
    }
}
