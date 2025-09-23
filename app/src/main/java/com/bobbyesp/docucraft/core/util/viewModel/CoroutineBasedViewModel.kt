package com.bobbyesp.docucraft.core.util.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class CoroutineBasedViewModel() : ViewModel() {
    abstract val exceptionHandler: CoroutineExceptionHandler

    protected fun ViewModel.launchIO(block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) { block() }
    }

    // For operations that don't need IO dispatcher
    protected fun ViewModel.launchSafe(block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch(exceptionHandler) { block() }
    }
}
