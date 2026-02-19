package com.bobbyesp.docucraft.core.util.state

sealed class ScreenState<out T> {
    abstract val data: T?
    abstract val message: String?
    abstract val error: Throwable?

    data class Success<T>(override val data: T) : ScreenState<T>() {
        override val message: String? = null
        override val error: Throwable? = null
    }

    data class Error<T>(
        override val message: String,
        override val data: T? = null,
        override val error: Throwable? = null
    ) : ScreenState<T>()

    data class Loading<T>(override val data: T? = null) : ScreenState<T>() {
        override val message: String? = null
        override val error: Throwable? = null
    }

    class Idle<T> : ScreenState<T>() {
        override val data: T? = null
        override val message: String? = null
        override val error: Throwable? = null
    }
}