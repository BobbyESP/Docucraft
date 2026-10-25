/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.widgets.di

import android.content.ComponentName
import com.bobbyesp.docucraft.feature.docscanner.presentation.widgets.RecentDocumentsWidgetReceiver
import com.bobbyesp.docucraft.feature.widgets.data.AppWidgetHomeScreenWidgets
import com.bobbyesp.docucraft.feature.widgets.domain.HomeScreenWidgets
import com.bobbyesp.docucraft.feature.widgets.domain.KeepWidgetsCurrentUseCase
import com.bobbyesp.docucraft.feature.widgets.domain.ObserveWidgetDocumentsUseCase
import com.bobbyesp.docucraft.feature.widgets.presentation.RecentDocumentsWidgetRenderer
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * The home-screen widgets: how they are told the library changed, and how the one that shows it is
 * drawn.
 */
val widgetsModule = module {
    // The receivers listed are the widgets that show the library, and so have to be drawn again
    // when it changes. The scan widget shows nothing of it.
    single<HomeScreenWidgets> {
        AppWidgetHomeScreenWidgets(
            context = androidContext(),
            libraryWidgets =
                listOf(ComponentName(androidContext(), RecentDocumentsWidgetReceiver::class.java)),
        )
    }
    factory {
        RecentDocumentsWidgetRenderer(
            context = androidContext(),
            observeWidgetDocuments = get(),
            thumbnails = get(),
            imageLoader = get(),
        )
    }
    factory { ObserveWidgetDocumentsUseCase(activity = get()) }
    factory { KeepWidgetsCurrentUseCase(observeWidgetDocuments = get(), widgets = get()) }
}
