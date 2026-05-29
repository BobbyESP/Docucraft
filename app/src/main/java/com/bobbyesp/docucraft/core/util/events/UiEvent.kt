/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.util.events

import com.bobbyesp.docucraft.core.domain.notifications.NotificationType

sealed interface UiEvent {
    data class ShowMessage(
        val message: String,
        val type: NotificationType = NotificationType.Normal,
    ) : UiEvent
}
