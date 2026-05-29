/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.domain.repository

import com.bobbyesp.docucraft.core.domain.notifications.InAppNotification

interface InAppNotificationsService {
    fun show(notification: InAppNotification)

    fun dismiss(id: String)

    fun dismissAll()
}
