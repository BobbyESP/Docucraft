package com.bobbyesp.docucraft.core.domain.usecase

import android.content.Context
import androidx.annotation.StringRes
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.notifications.InAppNotification
import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.domain.repository.InAppNotificationsService

class ShowSimpleNotificationUseCase(
    private val context: Context,
    private val inAppNotificationsService: InAppNotificationsService
) {
    operator fun invoke(@StringRes resId: Int, vararg formatArgs: Any, type: NotificationType = NotificationType.Normal) {
        inAppNotificationsService.show(
            InAppNotification(
                message = context.getString(resId, *formatArgs),
                type = type,
            )
        )
    }

    operator fun invoke(throwable: Throwable) {
        inAppNotificationsService.show(
            InAppNotification(
                message = throwable.message ?: context.getString(R.string.unknown_error),
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