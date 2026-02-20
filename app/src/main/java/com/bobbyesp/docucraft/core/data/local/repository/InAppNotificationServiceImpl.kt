package com.bobbyesp.docucraft.core.data.local.repository

import com.bobbyesp.docucraft.core.domain.notifications.InAppNotification
import com.bobbyesp.docucraft.core.domain.repository.InAppNotificationsService
import com.dokar.sonner.ToasterState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class InAppNotificationServiceImpl: InAppNotificationsService {

    val coroutineScope = CoroutineScope(
        context = Dispatchers.Main
    )

    val sonnerState = ToasterState(
        coroutineScope = coroutineScope,
        onDismissed = null,
    )

    override fun show(notification: InAppNotification) {
        sonnerState.show(
            id = notification.id,
            message = notification.message,
            icon = notification.icon,
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