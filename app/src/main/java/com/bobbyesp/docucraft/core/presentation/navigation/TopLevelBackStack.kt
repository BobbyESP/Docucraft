package com.bobbyesp.docucraft.core.presentation.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Manages a back stack with support for top-level navigation items.
 *
 * This class allows for maintaining separate back stacks for different top-level sections of an
 * application, while also providing a unified back stack for display purposes.
 *
 * @param T The type of the keys used to identify navigation destinations. Must be a non-nullable
 *   type.
 * @param startKey The key of the initial top-level destination.
 */
@Stable
class TopLevelBackStack<T : Any>(startKey: T) {

    // Maintain a stack for each top level route
    private var topLevelStacks: LinkedHashMap<T, SnapshotStateList<T>> =
        linkedMapOf(startKey to mutableStateListOf(startKey))

    // Expose the current top level route for consumers
    var topLevelKey by mutableStateOf(startKey)
        private set

    // Expose the back stack so it can be rendered by the NavDisplay
    val backStack = mutableStateListOf(startKey)

    /*
     * Updates the back stack to reflect the current state of the top-level stacks.
     * We re-add the whole list since in terms of a balance between stability and performance
     * is better than having to rearrange the list and sync partial changes (insertion/deletion).
     */
    private fun updateBackStack() =
        backStack.apply {
            clear()
            addAll(topLevelStacks.flatMap { it.value })
        }

    /**
     * Adds a new top-level navigation destination or moves an existing one to the front.
     *
     * If a top-level destination with the given [key] does not exist, it is created with a back
     * stack containing only itself.
     *
     * If a top-level destination with the given [key] already exists, its associated back stack is
     * moved to be the most recently accessed top-level stack.
     *
     * After adding or moving, the [key] becomes the current [topLevelKey] and the [backStack] is
     * updated.
     *
     * @param key The key of the top-level destination to add or move.
     */
    fun addTopLevel(key: T) {

        // If the top level doesn't exist, add it
        if (topLevelStacks[key] == null) {
            topLevelStacks[key] = mutableStateListOf(key)
        } else {
            // Otherwise just move it to the end of the stacks
            topLevelStacks.apply { remove(key)?.let { put(key, it) } }
        }
        topLevelKey = key
        updateBackStack()
    }

    /**
     * Adds a new key to the back stack of the current top-level destination.
     *
     * @param key The key to add to the back stack.
     */
    fun add(key: T) {
        topLevelStacks[topLevelKey]?.add(key)
        updateBackStack()
    }

    /**
     * Removes the last key from the current top-level back stack.
     *
     * If the removed key was also a top-level key (i.e., it was the only key in its stack), the
     * entire top-level stack associated with that key is removed. The `topLevelKey` is then updated
     * to the last remaining top-level key. Finally, the unified `backStack` is updated to reflect
     * the changes.
     */
    fun removeLast() {
        val removedKey = topLevelStacks[topLevelKey]?.removeLastOrNull()
        // If the removed key was a top level key, remove the associated top level stack
        topLevelStacks.remove(removedKey)
        topLevelKey = topLevelStacks.keys.last()
        updateBackStack()
    }
}
