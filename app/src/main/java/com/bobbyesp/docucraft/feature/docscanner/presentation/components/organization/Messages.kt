/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.bobbyesp.docucraft.core.domain.notifications.InAppNotification
import com.bobbyesp.docucraft.core.presentation.common.LocalNotificationsService
import com.bobbyesp.docucraft.core.util.events.UiEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/** Shows what a ViewModel has to tell the user, for as long as its screen or overlay is there. */
@Composable
fun ShowMessages(events: Flow<UiEvent>) {
    val notifications = LocalNotificationsService.current

    LaunchedEffect(events) {
        events.collectLatest { event ->
            when (event) {
                is UiEvent.ShowMessage ->
                    notifications.show(
                        InAppNotification(message = event.message, type = event.type)
                    )
            }
        }
    }
}
