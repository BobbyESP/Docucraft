package com.bobbyesp.docucraft.core.util.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobbyesp.docucraft.core.util.events.UiEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

abstract class BaseViewModel<Intent : Any, State : Any, Effect : Any>(
    initialState: State
) : ViewModel() {

    // ---------------- STATE ----------------

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<State> = _state.asStateFlow()

    protected val currentState: State
        get() = _state.value

    // ---------------- EFFECTS ----------------

    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects: Flow<Effect> = _effects.receiveAsFlow()

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

    protected fun sendEffect(effect: Effect) {
        viewModelScope.launch {
            _effects.send(effect)
        }
    }

    protected fun sendUiEvent(event: UiEvent) {
        viewModelScope.launch {
            _defaultUiEvents.send(event)
        }
    }

    // ---------------- SAFE LAUNCH ----------------

    protected fun launch(
        onError: ((Throwable) -> Unit)? = null,
        context: CoroutineContext = viewModelScope.coroutineContext,
        block: suspend CoroutineScope.() -> Unit
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
        //Optional override
    }
}