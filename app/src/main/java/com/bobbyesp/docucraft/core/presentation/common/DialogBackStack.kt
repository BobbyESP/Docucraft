package com.bobbyesp.docucraft.core.presentation.common

import androidx.compose.runtime.Immutable

@Immutable
data class DialogBackStack<T>(
    val stack: List<T> = emptyList()
) {
    val active: T? = stack.lastOrNull()
    val isVisible: Boolean = stack.isNotEmpty()

    fun push(dialog: T): DialogBackStack<T> = copy(stack = stack + dialog)

    fun pop(): DialogBackStack<T> = copy(stack = stack.dropLast(1))

    fun clear(): DialogBackStack<T> = copy(stack = emptyList())
}
