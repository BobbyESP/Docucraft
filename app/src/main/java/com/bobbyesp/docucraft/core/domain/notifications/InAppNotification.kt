package com.bobbyesp.docucraft.core.domain.notifications

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Immutable
data class InAppNotification(
    val id: String = System.nanoTime().toString(),
    val title: String? = null,
    val message: String,
    val icon: ImageVector? = null,
    val type: NotificationType = NotificationType.Normal,
    val action: NotificationAction? = null,
    val duration: Duration = 4000.milliseconds,
    val onDismiss: (() -> Unit)? = null
)
