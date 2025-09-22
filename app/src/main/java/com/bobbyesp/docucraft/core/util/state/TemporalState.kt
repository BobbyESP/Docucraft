package com.bobbyesp.docucraft.core.util.state

/**
 * Sealed interface for representing the presence state of a ViewModel item over time.
 *
 * This interface is used to represent whether a value of type [T] is currently present or not
 * within a given context, often used in ViewModels to handle asynchronous data loading or changes
 * in data availability.
 *
 * It offers two distinct states:
 * - [NotPresent]: Indicates that a value is currently absent. This could mean that the data hasn't
 *   been loaded yet, has been cleared, or doesn't exist.
 * - [Present]: Indicates that a value of type [T] is currently available. The actual value can be
 *   accessed via the `value` property.
 *
 * This approach allows for a more type-safe and explicit way to handle situations where data might
 * not be immediately available, avoiding the need for nullable types in some cases.
 *
 * Example usage:
 * ```kotlin
 * when (val state = myTemporalState) {
 *     is TemporalState.NotPresent -> {
 *         // Handle the case where the data is not present
 *         println("Data is not available yet.")
 *     }
 *     is TemporalState.Present -> {
 *         // Handle the case where the data is present
 *         val data = state.value
 *         println("Data is: $data")
 *     }
 * }
 * ```
 *
 * @param T The type of the value that might be present.
 */
sealed interface TemporalState<out T> {
    object NotPresent : TemporalState<Nothing>

    data class Present<out T>(val value: T) : TemporalState<T>
}
