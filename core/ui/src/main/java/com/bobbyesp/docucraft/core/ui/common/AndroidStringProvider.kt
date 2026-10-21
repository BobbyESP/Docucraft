/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.ui.common

import android.content.Context
import com.bobbyesp.docucraft.core.designsystem.R as DesignsystemR
import com.bobbyesp.docucraft.core.domain.StringProvider

class AndroidStringProvider(private val context: Context) : StringProvider {
    override fun get(id: Int, vararg args: Any): String {
        return context.getString(id, *args)
    }

    override fun getError(throwable: Throwable): String {
        return throwable.message ?: get(DesignsystemR.string.unknown_error)
    }

    override fun getError(id: Int, vararg args: Any, throwable: Throwable): String {
        return context.getString(
            id,
            *args,
            throwable.message ?: get(DesignsystemR.string.unknown_error),
        )
    }
}
