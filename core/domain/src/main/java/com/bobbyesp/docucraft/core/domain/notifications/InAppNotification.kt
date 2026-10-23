/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.domain.notifications

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class InAppNotification(
    val id: String = System.nanoTime().toString(),
    val title: String? = null,
    val message: String,
    val type: NotificationType = NotificationType.Normal,
    val action: NotificationAction? = null,
    val duration: Duration = 4000.milliseconds,
    val onDismiss: (() -> Unit)? = null,
)
