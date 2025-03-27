package com.bobbyesp.docucraft.core.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class ViewModelCoroutineBased() : ViewModel() {
    abstract val exceptionHandler: CoroutineExceptionHandler

    // Add this at the top of your ViewModel class
    protected fun ViewModel.launchIO(block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            block()
        }
    }

    // For operations that don't need IO dispatcher
    protected fun ViewModel.launchSafe(block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch(exceptionHandler) {
            block()
        }
    }
}