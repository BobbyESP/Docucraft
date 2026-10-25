/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.requests

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.core.designsystem.R as DesignsystemR
import com.bobbyesp.docucraft.core.domain.notifications.InAppNotification
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.navigation.Navigator
import com.bobbyesp.docucraft.core.navigation.keys.DocumentSearch
import com.bobbyesp.docucraft.core.navigation.keys.PdfViewer
import com.bobbyesp.docucraft.core.ui.common.LocalNotificationsService
import com.bobbyesp.docucraft.feature.home.navigation.Home
import com.bobbyesp.docucraft.library.domain.usecase.ObserveDocumentUseCase
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject

/**
 * Takes the user where something outside the UI asked to go: a document, or the search.
 *
 * Hosted by the shell and drawing nothing, as [ScanRequestNavigation] is, because where a request
 * lands is a question about the back stack. A document is looked up before it is opened: a widget
 * shows the library as it was when it was last drawn, so what it names may since have gone to the
 * bin, and opening a viewer only for it to close itself would say nothing about why.
 */
@Composable
fun LibraryEntryRequestNavigation(navigator: Navigator) {
    val requests: LibraryEntryRequests = koinInject()
    val observeDocument: ObserveDocumentUseCase = koinInject()
    val notifications = LocalNotificationsService.current
    val documentGone = stringResource(DesignsystemR.string.viewer_error_not_found)

    LaunchedEffect(requests, navigator, notifications, documentGone) {
        requests.pending.collect { pending ->
            if (pending == null) return@collect
            when (val request = requests.take()) {
                null -> Unit
                LibraryEntryRequest.Search -> navigator.enterFromHome(DocumentSearch)
                is LibraryEntryRequest.OpenDocument ->
                    if (observeDocument(request.documentUuid).first() != null) {
                        navigator.enterFromHome(PdfViewer(request.documentUuid))
                    } else {
                        notifications.show(
                            InAppNotification(message = documentGone, type = NotificationType.Error)
                        )
                    }
            }
        }
    }
}

/**
 * Goes to [key] as if from Home, whatever was open: back from a document opened by a widget leads
 * to the library, not to a sheet about some other document the user had left open days ago.
 */
internal fun Navigator.enterFromHome(key: NavKey) {
    goBackWhile { it != Home }
    goTo(key)
}
