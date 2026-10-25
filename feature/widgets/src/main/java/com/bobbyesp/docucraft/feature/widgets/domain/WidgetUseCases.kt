/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.widgets.domain

import com.bobbyesp.docucraft.library.domain.model.Document
import com.bobbyesp.docucraft.library.domain.repository.DocumentActivityRepository
import com.bobbyesp.docucraft.library.domain.usecase.RegisterLinkedDocumentUseCase
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The documents a home-screen widget lists: the library's own, used last, most recent first.
 *
 * Not everything Recents shows in the app:
 * - a document of another app is left out. A widget stays on the home screen for anyone holding the
 *   phone to read, and the name of something another app shared once is not the library's to put
 *   there;
 * - so is one whose file was not there the last time it was looked for, because a row on the home
 *   screen has no room to say why it would not open.
 */
class ObserveWidgetDocumentsUseCase(private val activity: DocumentActivityRepository) {

    operator fun invoke(limit: Int): Flow<List<WidgetDocument>> =
        activity
            // Recents is cut before it gets here, and the documents of other apps in it would take
            // the library's places: enough is asked for that they cannot.
            .observeRecents(limit + RegisterLinkedDocumentUseCase.MAX_LINKED_DOCUMENTS)
            .map { recents ->
                recents
                    .filter { it.isReachable }
                    .mapNotNull { recent ->
                        (recent.document as? Document.Managed)?.let { document ->
                            WidgetDocument(
                                uuid = document.uuid,
                                name = document.name,
                                pageCount = document.pageCount,
                                lastUsedAtEpochMillis =
                                    recent.lastOpenedAtEpochMillis ?: document.createdAtEpochMillis,
                                thumbnail = document.thumbnail,
                            )
                        }
                    }
                    .take(limit)
            }
            .distinctUntilChanged()
}

/**
 * Keeps the home-screen widgets showing the library as it is, for as long as it is collected: the
 * app runs it for the life of its process.
 *
 * A change is waited on before the widgets are told, because changes come in bursts: saving a scan
 * writes the document, then its page count, then its title. Each refresh is the launcher drawing
 * every widget again, so a burst is one refresh and not five.
 */
class KeepWidgetsCurrentUseCase(
    private val observeWidgetDocuments: ObserveWidgetDocumentsUseCase,
    private val widgets: HomeScreenWidgets,
) {

    @OptIn(FlowPreview::class)
    suspend operator fun invoke(limit: Int = WIDGET_DOCUMENTS, settle: Duration = SETTLE) {
        // The first answer counts too: the process may have ended last time between a change and
        // telling the widgets, and this is where they catch up.
        observeWidgetDocuments(limit).debounce(settle).collect { widgets.refresh() }
    }

    companion object {
        /** As many as the tallest widget a home screen holds can show without much scrolling. */
        const val WIDGET_DOCUMENTS = 10

        private val SETTLE = 750.milliseconds
    }
}
