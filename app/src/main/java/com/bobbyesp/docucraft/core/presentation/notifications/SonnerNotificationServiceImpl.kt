package com.bobbyesp.docucraft.core.presentation.notifications

import com.bobbyesp.docucraft.core.domain.notifications.InAppNotification
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType.*
import com.bobbyesp.docucraft.core.domain.repository.InAppNotificationsService
import com.dokar.sonner.TextToastAction
import com.dokar.sonner.Toast
import com.dokar.sonner.ToastType
import com.dokar.sonner.ToasterState
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap

class SonnerNotificationServiceImpl(
    private val coroutineScope: CoroutineScope,
) : InAppNotificationsService {

    private val dismissCallbacks = ConcurrentHashMap<Any, () -> Unit>()

    val sonnerState = ToasterState(
        coroutineScope = coroutineScope,
        onDismissed = { toast ->
            dismissCallbacks.remove(toast.id)?.invoke()
        }
    )

    private fun NotificationType.toSonnerType(): ToastType {
        return when (this) {
            Normal -> ToastType.Normal
            Success -> ToastType.Success
            Info -> ToastType.Info
            Warning -> ToastType.Warning
            Error -> ToastType.Error
        }
    }

    override fun show(notification: InAppNotification) {
        notification.onDismiss?.let {
            dismissCallbacks[notification.id] = it
        }

        // Handle title if present
        val displayMessage = if (!notification.title.isNullOrBlank()) {
            "${notification.title}\n${notification.message}"
        } else {
            notification.message
        }

        val toast = Toast(
            id = notification.id,
            message = displayMessage,
            icon = notification.icon,
            action = notification.action?.let { action ->
                TextToastAction(
                    text = action.label,
                    onClick = {
                        action.onAction()
                        if (action.dismissOnClick) {
                            sonnerState.dismiss(notification.id)
                        }
                    }
                )
            },
            type = notification.type.toSonnerType(),
            duration = notification.duration,
        )
        sonnerState.show(toast)
    }

    override fun dismiss(id: String) {
        sonnerState.dismiss(id)
    }

    override fun dismissAll() {
        dismissCallbacks.clear()
        sonnerState.dismissAll()
    }
}
