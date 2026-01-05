package com.bobbyesp.docucraft.core.util

sealed class Resource<T>(val data: T? = null, val message: String? = null, val error: Throwable? = null) {
    class Success<T>(data: T) : Resource<T>(data)
    class Error<T>(message: String, data: T? = null, error: Throwable? = null) : Resource<T>(data, message, error)
    class Loading<T>(data: T? = null) : Resource<T>(data)
    class Idle<T> : Resource<T>()
}
