package com.bobbyesp.docucraft.core.presentation.notifications

import com.bobbyesp.docucraft.core.domain.notifications.InAppNotification
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType.Info
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType.Normal
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType.Success
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType.Warning
import com.bobbyesp.docucraft.core.domain.repository.InAppNotificationsService
import com.dokar.sonner.ToastType
import com.dokar.sonner.ToasterState
import kotlinx.coroutines.CoroutineScope

class SonnerNotificationServiceImpl(
    private val coroutineScope: CoroutineScope,
) : InAppNotificationsService {

    val sonnerState = ToasterState(
        coroutineScope = coroutineScope,
        onDismissed = null
    )

    private fun NotificationType.toSonnerType(): ToastType {
        return when (this) {
            Normal -> ToastType.Normal
            Success -> ToastType.Success
            Info -> ToastType.Info
            Warning -> ToastType.Warning
            NotificationType.Error -> ToastType.Error
        }
    }

    override fun show(notification: InAppNotification) {
        sonnerState.show(
            id = notification.id,
            message = notification.message,
            action = notification.action,
            type = notification.type.toSonnerType(),
            duration = notification.duration,
        )
    }


    override fun dismiss(id: String) {
        sonnerState.dismiss(id)
    }

    override fun dismissAll() {
        sonnerState.dismissAll()
    }
}