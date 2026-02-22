package com.bobbyesp.docucraft.core.presentation.notifications

import android.content.Context
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.StringProvider

class AndroidStringProvider(
    private val context: Context
) : StringProvider {
    override fun get(id: Int, vararg args: Any): String {
        return context.getString(id, *args)
    }

    override fun getError(throwable: Throwable): String {
        return throwable.message ?: get(R.string.unknown_error)
    }
}