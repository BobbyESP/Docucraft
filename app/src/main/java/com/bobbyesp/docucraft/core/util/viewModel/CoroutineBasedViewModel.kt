package com.bobbyesp.docucraft.core.util.viewModel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * Enhanced base ViewModel with built-in coroutine management, state handling, and logging.
 *
 * Features:
 * - Automatic exception handling with logging
 * - Coroutine helpers (launchIO, launchSafe, launchWithRetry)
 * - UI state management helpers
 * - One-time events emission
 * - Debouncing support for search operations
 * - Automatic logging with class-based TAG
 */
abstract class CoroutineBasedViewModel : ViewModel() {

    /**
     * A customizable [CoroutineExceptionHandler] for all coroutines launched within this ViewModel.
     *
     * The default implementation logs the error using the ViewModel's [TAG] and then calls the
     * [onCoroutineException] callback, allowing for both centralized logging and specific handling
     * in subclasses.
     *
     * You can override this property in a subclass to provide a completely different exception
     * handling strategy.
     *
     * Example of overriding:
     * ```kotlin
     * override val exceptionHandler: CoroutineExceptionHandler
     *     get() = CoroutineExceptionHandler { _, throwable ->
     *         // Custom logic: e.g., send crash report and update UI state
     *         _uiState.value = UiState.Error("A critical error occurred.")
     *         Crashlytics.logException(throwable)
     *     }
     * ```
     */
    open val exceptionHandler: CoroutineExceptionHandler
        get() = CoroutineExceptionHandler { _, throwable ->
            logError("Coroutine exception: ${throwable.message}", throwable)
            onCoroutineException(throwable)
        }

    /** Automatically generated TAG based on the class name. */
    protected open val TAG: String
        get() = this::class.java.simpleName

    /** Called when a coroutine exception occurs. Override to handle globally. */
    protected open fun onCoroutineException(throwable: Throwable) {
        // Override in subclasses if needed
    }

    // ==================== Coroutine Launchers ====================

    /** Launch a coroutine on IO dispatcher with exception handling. */
    protected fun launchIO(block: suspend CoroutineScope.() -> Unit): Job {
        return viewModelScope.launch(Dispatchers.IO + exceptionHandler) { block() }
    }

    /** Launch a coroutine on Main dispatcher with exception handling. */
    protected fun launchMain(block: suspend CoroutineScope.() -> Unit): Job {
        return viewModelScope.launch(Dispatchers.Main + exceptionHandler) { block() }
    }

    /** Launch a coroutine on Default dispatcher with exception handling. */
    protected fun launchDefault(block: suspend CoroutineScope.() -> Unit): Job {
        return viewModelScope.launch(Dispatchers.Default + exceptionHandler) { block() }
    }

    /** Launch a coroutine with custom context and exception handling. */
    protected fun launchSafe(
        context: CoroutineContext = Dispatchers.Default,
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        return viewModelScope.launch(context + exceptionHandler) { block() }
    }

    /**
     * Launches a coroutine that automatically retries the given [block] of code on failure, using
     * an exponential backoff strategy.
     *
     * This is useful for operations that might temporarily fail due to network issues or other
     * transient problems. If the block throws an [Exception], it will be caught, and the operation
     * will be retried after a delay. The delay increases exponentially with each failed attempt.
     *
     * If all retry attempts are exhausted, the last caught exception is re-thrown and will be
     * handled by the ViewModel's global [exceptionHandler].
     *
     * @param retries The total number of times the operation should be attempted. A value of 3
     *   means one initial attempt and up to two retries. Defaults to 3.
     * @param initialDelay The initial delay in milliseconds before the first retry. Defaults to
     *   1000L.
     * @param maxDelay The maximum delay in milliseconds between retries. This caps the exponential
     *   growth. Defaults to 10000L.
     * @param factor The multiplicative factor for the delay. A factor of 2.0 means the delay
     *   doubles with each retry (e.g., 1s, 2s, 4s, ...). Defaults to 2.0.
     * @param context The [CoroutineContext] to execute the coroutine on. Defaults to
     *   [Dispatchers.IO], which is suitable for network or disk operations.
     * @param onRetry An optional callback lambda that is invoked right before a retry attempt. It
     *   receives the current attempt number (starting from 1) and the error that caused the
     *   failure. This can be used for logging or updating the UI about the retry attempt.
     */
    protected fun launchWithRetry(
        retries: Int = 3,
        initialDelay: Long = 1000L,
        maxDelay: Long = 10000L,
        factor: Double = 2.0,
        context: CoroutineContext = Dispatchers.IO,
        onRetry: ((attempt: Int, error: Throwable) -> Unit)? = null,
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        return viewModelScope.launch(context + exceptionHandler) {
            var currentDelay = initialDelay
            repeat(retries) { attempt ->
                try {
                    block()
                    return@launch
                } catch (e: Exception) {
                    if (attempt == retries - 1) {
                        throw e
                    }
                    onRetry?.invoke(attempt + 1, e)
                    logDebug("Retry attempt ${attempt + 1} after error: ${e.message}")
                    delay(currentDelay)
                    currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
                }
            }
        }
    }

    // ==================== StateFlow Helpers ====================

    /**
     * Creates a [MutableStateFlow] and its read-only [StateFlow] counterpart.
     *
     * This is a common pattern for managing UI state within a ViewModel. The mutable version is
     * kept private to the ViewModel to enforce unidirectional data flow, while the immutable
     * version is exposed to the UI for observation.
     *
     * Example usage in a ViewModel:
     * ```kotlin
     * private val _uiState, uiState = mutableStateFlowOf(MyUiState.Loading)
     * val viewState: StateFlow<MyUiState> = uiState // Expose the read-only flow
     *
     * fun updateState() {
     *     _uiState.value = MyUiState.Success(...) // Modify the private mutable flow
     * }
     * ```
     *
     * @param T The type of the state.
     * @param initialValue The initial value of the state.
     * @return A [Pair] containing the [MutableStateFlow] as its `first` element and the
     *   corresponding immutable [StateFlow] as its `second` element.
     */
    protected fun <T> mutableStateFlowOf(initialValue: T): Pair<MutableStateFlow<T>, StateFlow<T>> {
        val mutable = MutableStateFlow(initialValue)
        return mutable to mutable.asStateFlow()
    }

    // ==================== SharedFlow Helpers (Events) ====================

    /**
     * Creates a [MutableSharedFlow] and its corresponding immutable [SharedFlow] pair, specifically
     * configured for handling one-time UI events (like showing a toast, navigating, or displaying a
     * dialog).
     *
     * The flow is configured with:
     * - `replay = 0`: Ensures that new subscribers do not receive previously emitted events.
     * - `extraBufferCapacity = 1`: Allows one event to be buffered without suspending the sender,
     *   which helps prevent losing events if they are emitted in rapid succession before a
     *   collector is ready.
     *
     * This setup is ideal for "fire-and-forget" events that should only be consumed once.
     *
     * @return A [Pair] containing the [MutableSharedFlow] for emitting events and the immutable
     *   [SharedFlow] for collecting them in the UI layer.
     */
    protected fun <T> createEventFlow(): Pair<MutableSharedFlow<T>, SharedFlow<T>> {
        val mutable = MutableSharedFlow<T>(replay = 0, extraBufferCapacity = 1)
        return mutable to mutable.asSharedFlow()
    }

    /** Emit an event safely (won't suspend). */
    protected fun <T> MutableSharedFlow<T>.emitEvent(event: T) {
        tryEmit(event)
    }

    // ==================== Async Operations with State ====================

    /**
     * Executes an asynchronous operation with built-in state management and callbacks. This
     * function simplifies handling common patterns for background tasks, such as showing/hiding a
     * loading indicator and processing success or failure outcomes.
     *
     * The operation is launched within the `viewModelScope` and uses a provided [CoroutineContext]
     * (defaulting to `Dispatchers.IO`). It automatically wraps the execution in a try-catch block
     * to handle exceptions.
     *
     * @param T The type of the result returned by the async operation.
     * @param context The [CoroutineContext] to run the operation on. Defaults to `Dispatchers.IO`.
     * @param loadingState An optional [MutableStateFlow]<Boolean> that will be updated to `true`
     *   before the operation starts and `false` after it completes (either successfully or with an
     *   error).
     * @param onLoading An optional lambda to be invoked just before the operation begins.
     * @param onSuccess An optional lambda that is called with the result `T` if the operation
     *   completes successfully.
     * @param onError An optional lambda that is called with the [Throwable] if the operation fails.
     *   This is in addition to the global [exceptionHandler].
     * @param block The suspend lambda containing the asynchronous logic to be executed. It should
     *   return a result of type `T`.
     * @return A [Job] representing the running coroutine.
     */
    protected fun <T> executeAsync(
        context: CoroutineContext = Dispatchers.IO,
        loadingState: MutableStateFlow<Boolean>? = null,
        onLoading: (() -> Unit)? = null,
        onSuccess: ((T) -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null,
        block: suspend CoroutineScope.() -> T,
    ): Job {
        return viewModelScope.launch(context + exceptionHandler) {
            try {
                loadingState?.value = true
                onLoading?.invoke()

                val result = block()

                loadingState?.value = false
                onSuccess?.invoke(result)
            } catch (e: Exception) {
                loadingState?.value = false
                onError?.invoke(e)
                logError("Async operation failed", e)
            }
        }
    }

    /**
     * Collects a [Flow] within the `viewModelScope` with built-in state management and error
     * handling. This is a safer alternative to a direct `collect` call within a ViewModel.
     *
     * The flow collection is wrapped in a `try-catch` like structure using Flow operators.
     * - `onStart` is used to signal the beginning of the collection (e.g., to show a loading
     *   indicator).
     * - `catch` handles any exceptions thrown by the upstream flow or during collection.
     * - The final `collect` block processes each emitted value.
     * - A final `onComplete` lambda is invoked after the flow has been fully collected (note: this
     *   does not run if the coroutine is cancelled).
     *
     * @param T The type of data emitted by the Flow.
     * @param onLoading A lambda to be invoked when the flow collection starts. Ideal for setting a
     *   loading state to `true`.
     * @param onEach A lambda to be invoked for each value emitted by the flow.
     * @param onError A lambda to handle any exceptions that occur during flow collection.
     * @param onComplete A lambda to be invoked after the flow has finished emitting all its items.
     * @return A [Job] representing the collection coroutine, which can be used to cancel it if
     *   needed.
     */
    protected fun <T> Flow<T>.collectSafely(
        onLoading: (() -> Unit)? = null,
        onEach: (T) -> Unit,
        onError: ((Throwable) -> Unit)? = null,
        onComplete: (() -> Unit)? = null,
    ): Job {
        return viewModelScope.launch(exceptionHandler) {
            this@collectSafely.onStart { onLoading?.invoke() }
                .catch { e ->
                    logError("Flow collection error", e)
                    onError?.invoke(e)
                }
                .collect { value -> onEach(value) }
            onComplete?.invoke()
        }
    }

    // ==================== Debouncing ====================

    private var debounceJob: Job? = null

    /**
     * Executes a block of code after a specified delay, cancelling any previously scheduled block
     * that hasn't run yet. This is particularly useful for operations like handling search input,
     * where you only want to perform an action (e.g., an API call) after the user has stopped
     * typing for a moment.
     *
     * Each call to `debounce` will cancel the previous pending operation and schedule a new one.
     *
     * Example Usage (in a ViewModel):
     * ```kotlin
     * fun onSearchQueryChanged(query: String) {
     *     debounce(500L) { // Wait for 500ms of inactivity
     *         // Perform the actual search operation here
     *         fetchSearchResults(query)
     *     }
     * }
     * ```
     *
     * @param delayMillis The debounce delay in milliseconds. The code block will execute after this
     *   period of inactivity. Defaults to 300ms.
     * @param context The [CoroutineContext] on which to execute the block. Defaults to
     *   `Dispatchers.Main`.
     * @param block The suspend lambda to be executed after the delay.
     */
    protected fun debounce(
        delayMillis: Long = 300L,
        context: CoroutineContext = Dispatchers.Main,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        debounceJob?.cancel()
        debounceJob =
            viewModelScope.launch(context + exceptionHandler) {
                delay(delayMillis)
                block()
            }
    }

    // ==================== Logging Helpers ====================

    protected fun logDebug(message: String) {
        Log.d(TAG, message)
    }

    protected fun logInfo(message: String) {
        Log.i(TAG, message)
    }

    protected fun logWarning(message: String, throwable: Throwable? = null) {
        Log.w(TAG, message, throwable)
    }

    protected fun logError(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }

    protected fun logVerbose(message: String) {
        Log.v(TAG, message)
    }

    // ==================== Lifecycle ====================

    override fun onCleared() {
        super.onCleared()
        debounceJob?.cancel()
        logDebug("ViewModel cleared")
    }

    // ==================== Deprecated Methods (for backward compatibility) ====================

    @Deprecated("Use launchIO() instead", ReplaceWith("launchIO(block)"), DeprecationLevel.WARNING)
    protected fun ViewModel.launchIO(block: suspend CoroutineScope.() -> Unit): Job {
        return viewModelScope.launch(Dispatchers.IO + exceptionHandler) { block() }
    }

    @Deprecated(
        "Use launchSafe() or launchMain() instead",
        ReplaceWith("launchMain(block)"),
        DeprecationLevel.WARNING,
    )
    protected fun ViewModel.launchSafe(block: suspend CoroutineScope.() -> Unit): Job {
        return viewModelScope.launch(exceptionHandler) { block() }
    }
}
