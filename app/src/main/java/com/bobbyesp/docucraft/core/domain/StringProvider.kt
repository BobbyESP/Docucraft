package com.bobbyesp.docucraft.core.domain

import androidx.annotation.StringRes

interface StringProvider {
    fun get(@StringRes id: Int, vararg args: Any): String
    fun getError(throwable: Throwable): String
}