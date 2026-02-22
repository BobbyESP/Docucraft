package com.bobbyesp.docucraft.core.util.events

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

/**
 * A Composable side-effect that collects a [Flow] of events and executes [onEvent] for each emission.
 *
 * This function is designed for handling "one-off" events sent from a ViewModel, such as navigation,
 * showing a Snackbar, or triggering a dialog, which should not be represented as UI state.
 *
 * The collection is lifecycle-aware and will be active only when the Composable is in the
 * [Lifecycle.State.STARTED] state. The underlying coroutine is automatically cancelled when the
 * Composable leaves the composition.
 *
 * The effect will restart if the [Flow] instance itself or any of the optional [keys] change.
 *
 * @param T The type of the event.
 * @param keys Optional keys to control the restart of the effect. The effect will be
 * relaunched if any of these keys change.
 * @param onEvent A suspending lambda to be executed for each event. It runs in the context of the
 * `LaunchedEffect`.
 */
@Suppress("ModifierRequired")
@Composable
fun <T> Flow<T>.CollectEventsEffect(
    vararg keys: Any?,
    onEvent: suspend (T) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val flow = this // The receiver Flow

    LaunchedEffect(flow, *keys) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect(onEvent)
        }
    }
}
