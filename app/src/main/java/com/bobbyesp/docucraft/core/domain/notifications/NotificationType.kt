package com.bobbyesp.docucraft.core.domain.notifications

import com.dokar.sonner.ToastType

enum class NotificationType {
    Normal,
    Success,
    Info,
    Warning,
    Error;

    fun toSonnerType(): ToastType {
        return when (this) {
            Normal -> ToastType.Normal
            Success -> ToastType.Success
            Info -> ToastType.Info
            Warning -> ToastType.Warning
            Error -> ToastType.Error
        }
    }
}
