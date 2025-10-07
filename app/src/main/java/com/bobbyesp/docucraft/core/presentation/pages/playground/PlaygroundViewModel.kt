package com.bobbyesp.docucraft.core.presentation.pages.playground

import android.util.Log
import com.bobbyesp.docucraft.core.util.viewModel.CoroutineBasedViewModel
import kotlinx.coroutines.CoroutineExceptionHandler

class PlaygroundViewModel : CoroutineBasedViewModel() {
    override val exceptionHandler: CoroutineExceptionHandler
        get() = CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Coroutine exception: ${throwable.message}", throwable)
        }

    companion object {
        const val TAG = "PlaygroundViewModel"
    }
}
