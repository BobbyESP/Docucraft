package com.bobbyesp.docucraft.core.domain.usecase

import androidx.annotation.StringRes
import com.bobbyesp.docucraft.core.domain.StringProvider
import com.bobbyesp.docucraft.core.domain.notifications.InAppNotification
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.domain.repository.InAppNotificationsService

class NotifyUserUseCase(
    private val stringProvider: StringProvider,
    private val inAppNotificationsService: InAppNotificationsService
) {
    operator fun invoke(
        @StringRes resId: Int,
        vararg formatArgs: Any,
        type: NotificationType = NotificationType.Normal
    ) {
        inAppNotificationsService.show(
            InAppNotification(
                message = stringProvider.get(resId, *formatArgs),
                type = type,
            )
        )
    }

    operator fun invoke(throwable: Throwable) {
        inAppNotificationsService.show(
            InAppNotification(
                message = stringProvider.getError(throwable),
                type = NotificationType.Error,
            )
        )
    }

    operator fun invoke(message: String, type: NotificationType = NotificationType.Normal) {
        inAppNotificationsService.show(
            InAppNotification(
                message = message,
                type = type,
            )
        )
    }
}