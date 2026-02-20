package com.bobbyesp.docucraft.core.domain.notifications

import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class InAppNotification(
    val id: String = System.nanoTime().toString(),
    val message: String,
    val icon: ImageVector? = null,
    val type: NotificationType = NotificationType.Normal,
    val action: NotificationAction? = null,
    val duration: Duration = 4000.milliseconds
)
