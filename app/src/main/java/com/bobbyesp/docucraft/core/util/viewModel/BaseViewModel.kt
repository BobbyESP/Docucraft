/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.util.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobbyesp.docucraft.core.util.events.UiEvent
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class BaseViewModel<Intent : Any, State : Any, Effect : Any>(initialState: State) :
    ViewModel() {

    // ---------------- STATE ----------------

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<State> = _state.asStateFlow()

    protected val currentState: State
        get() = _state.value

    // ---------------- EFFECTS ----------------

    /**
     * Things for whoever is on screen *now* to do — close this, go there.
     *
     * Dropped when nobody is listening, which is the whole point. These used to share the queue
     * below, and a queue is exactly wrong for a command: one emitted while its screen was off
     * screen waited, and was then carried out against whatever the user happened to be doing when
     * the screen came back. A command nobody took is a command that no longer applies.
     *
     * `replay = 0` so a collector that attaches later learns nothing of what it missed, and a
     * buffer of one with [BufferOverflow.DROP_OLDEST] so emitting never suspends and never queues
     * up behind a slow collector.
     */
    private val _effects =
        MutableSharedFlow<Effect>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val effects: SharedFlow<Effect> = _effects.asSharedFlow()

    /**
     * Things to tell the user, which wait until there is somebody to tell.
     *
     * The opposite requirement, hence the separate pipe: a scan that finished while the catalogue
     * was off screen still has to report what happened when the user comes back. Losing that is a
     * silent failure; carrying out a stale command is a wrong action. Only one of the two is worth
     * keeping.
     */
    private val _defaultUiEvents = Channel<UiEvent>(Channel.BUFFERED)
    val defaultEvents: Flow<UiEvent> = _defaultUiEvents.receiveAsFlow()

    // ---------------- INTENTS ----------------

    fun onSendIntent(intent: Intent) {
        onHandleIntent(intent)
    }

    protected abstract fun onHandleIntent(intent: Intent)

    // ---------------- REDUCER ----------------

    protected fun setState(reducer: State.() -> State) {
        _state.update { current -> current.reducer() }
    }

    // ---------------- EFFECT EMITTER ----------------

    /**
     * Emits without suspending, so an effect is raised in the same breath as whatever caused it
     * rather than on a coroutine that may run after the screen has moved on.
     */
    protected fun sendEffect(effect: Effect) {
        _effects.tryEmit(effect)
    }

    protected fun sendUiEvent(event: UiEvent) {
        viewModelScope.launch { _defaultUiEvents.send(event) }
    }

    // ---------------- SAFE LAUNCH ----------------

    protected fun launch(
        onError: ((Throwable) -> Unit)? = null,
        context: CoroutineContext = viewModelScope.coroutineContext,
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        return viewModelScope.launch(context) {
            runCatching { block() }
                .onFailure { error ->
                    onError?.invoke(error)
                    handleError(error)
                }
        }
    }

    // ---------------- ERROR HOOK ----------------

    protected open fun handleError(throwable: Throwable) {
        // Optional override
    }
}
