package com.bobbyesp.docucraft.core.domain.notifications

/**
 * Represents an action that can be performed on a notification.
 * @property label The text to display on the action button.
 * @property dismissOnClick Whether the notification should be dismissed when the action is clicked.
 * @property onAction The callback to invoke when the action is clicked.
 */
data class NotificationAction(
    val label: String,
    val dismissOnClick: Boolean = true,
    val onAction: () -> Unit
)
