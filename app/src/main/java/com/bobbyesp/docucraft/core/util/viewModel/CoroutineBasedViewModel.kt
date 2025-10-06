package com.bobbyesp.docucraft.core.util.viewModel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlin.coroutines.CoroutineContext

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
     * Override this to customize exception handling.
     * Default implementation logs errors with the class name as TAG.
     */
    open val exceptionHandler: CoroutineExceptionHandler
        get() = CoroutineExceptionHandler { _, throwable ->
            logError("Coroutine exception: ${throwable.message}", throwable)
            onCoroutineException(throwable)
        }

    /**
     * Automatically generated TAG based on the class name.
     */
    protected open val TAG: String
        get() = this::class.java.simpleName

    /**
     * Called when a coroutine exception occurs. Override to handle globally.
     */
    protected open fun onCoroutineException(throwable: Throwable) {
        // Override in subclasses if needed
    }

    // ==================== Coroutine Launchers ====================

    /**
     * Launch a coroutine on IO dispatcher with exception handling.
     */
    protected fun launchIO(block: suspend CoroutineScope.() -> Unit): Job {
        return viewModelScope.launch(Dispatchers.IO + exceptionHandler) { block() }
    }

    /**
     * Launch a coroutine on Main dispatcher with exception handling.
     */
    protected fun launchMain(block: suspend CoroutineScope.() -> Unit): Job {
        return viewModelScope.launch(Dispatchers.Main + exceptionHandler) { block() }
    }

    /**
     * Launch a coroutine on Default dispatcher with exception handling.
     */
    protected fun launchDefault(block: suspend CoroutineScope.() -> Unit): Job {
        return viewModelScope.launch(Dispatchers.Default + exceptionHandler) { block() }
    }

    /**
     * Launch a coroutine with custom context and exception handling.
     */
    protected fun launchSafe(
        context: CoroutineContext = Dispatchers.Default,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return viewModelScope.launch(context + exceptionHandler) { block() }
    }

    /**
     * Launch a coroutine with automatic retry on failure.
     *
     * @param retries Number of retry attempts
     * @param initialDelay Initial delay before first retry in milliseconds
     * @param maxDelay Maximum delay between retries in milliseconds
     * @param factor Multiplication factor for delay (exponential backoff)
     * @param context Coroutine context to use
     * @param block The suspending function to execute
     */
    protected fun launchWithRetry(
        retries: Int = 3,
        initialDelay: Long = 1000L,
        maxDelay: Long = 10000L,
        factor: Double = 2.0,
        context: CoroutineContext = Dispatchers.IO,
        onRetry: ((attempt: Int, error: Throwable) -> Unit)? = null,
        block: suspend CoroutineScope.() -> Unit
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
     * Create a MutableStateFlow and expose it as immutable StateFlow.
     */
    protected fun <T> mutableStateFlowOf(initialValue: T): Pair<MutableStateFlow<T>, StateFlow<T>> {
        val mutable = MutableStateFlow(initialValue)
        return mutable to mutable.asStateFlow()
    }

    /**
     * Update a MutableStateFlow value safely.
     */
    protected fun <T> MutableStateFlow<T>.updateValue(transform: (T) -> T) {
        this.value = transform(this.value)
    }

    // ==================== SharedFlow Helpers (Events) ====================

    /**
     * Create a SharedFlow for one-time events (like navigation, toasts, etc.).
     * Uses replay=0 and extraBufferCapacity=1 to ensure events are not missed.
     */
    protected fun <T> createEventFlow(): Pair<MutableSharedFlow<T>, SharedFlow<T>> {
        val mutable = MutableSharedFlow<T>(
            replay = 0,
            extraBufferCapacity = 1
        )
        return mutable to mutable.asSharedFlow()
    }

    /**
     * Emit an event safely (won't suspend).
     */
    protected fun <T> MutableSharedFlow<T>.emitEvent(event: T) {
        tryEmit(event)
    }

    // ==================== Async Operations with State ====================

    /**
     * Execute an async operation with automatic loading state management.
     *
     * @param loadingState Optional MutableStateFlow to update with loading state
     * @param onLoading Called when operation starts
     * @param onSuccess Called when operation succeeds with result
     * @param onError Called when operation fails with exception
     * @param block The suspending function to execute
     */
    protected fun <T> executeAsync(
        context: CoroutineContext = Dispatchers.IO,
        loadingState: MutableStateFlow<Boolean>? = null,
        onLoading: (() -> Unit)? = null,
        onSuccess: ((T) -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null,
        block: suspend CoroutineScope.() -> T
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
     * Collect a Flow with automatic error handling and loading state.
     */
    protected fun <T> Flow<T>.collectSafely(
        onLoading: (() -> Unit)? = null,
        onEach: (T) -> Unit,
        onError: ((Throwable) -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ): Job {
        return viewModelScope.launch(exceptionHandler) {
            this@collectSafely
                .onStart { onLoading?.invoke() }
                .catch { e ->
                    logError("Flow collection error", e)
                    onError?.invoke(e)
                }
                .collect { value ->
                    onEach(value)
                }
            onComplete?.invoke()
        }
    }

    // ==================== Debouncing ====================

    private var debounceJob: Job? = null

    /**
     * Execute a block with debouncing. Useful for search queries.
     *
     * @param delayMillis Delay in milliseconds before executing
     * @param context Coroutine context to use
     * @param block The suspending function to execute
     */
    protected fun debounce(
        delayMillis: Long = 300L,
        context: CoroutineContext = Dispatchers.Main,
        block: suspend CoroutineScope.() -> Unit
    ) {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch(context + exceptionHandler) {
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

    @Deprecated(
        "Use launchIO() instead",
        ReplaceWith("launchIO(block)"),
        DeprecationLevel.WARNING
    )
    protected fun ViewModel.launchIO(block: suspend CoroutineScope.() -> Unit): Job {
        return viewModelScope.launch(Dispatchers.IO + exceptionHandler) { block() }
    }

    @Deprecated(
        "Use launchSafe() or launchMain() instead",
        ReplaceWith("launchMain(block)"),
        DeprecationLevel.WARNING
    )
    protected fun ViewModel.launchSafe(block: suspend CoroutineScope.() -> Unit): Job {
        return viewModelScope.launch(exceptionHandler) { block() }
    }
}
