package com.bobbyesp.docucraft.core.domain.notifications

import androidx.compose.runtime.Immutable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Immutable
data class InAppNotification(
    val id: String = System.nanoTime().toString(),
    val message: String,
    val type: NotificationType = NotificationType.Normal,
    val action: NotificationAction? = null,
    val duration: Duration = 4000.milliseconds
)
