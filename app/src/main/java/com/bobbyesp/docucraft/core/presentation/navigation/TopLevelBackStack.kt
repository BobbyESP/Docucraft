package com.bobbyesp.docucraft.core.presentation.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.toMutableStateList
import androidx.navigation3.runtime.NavKey

/**
 * Manages the back stack for a top-level navigation structure, such as a bottom navigation bar.
 *
 * This class maintains a separate back stack for each top-level destination (e.g., each tab).
 * It keeps track of the history of visited top-level destinations, allowing for proper "back"
 * navigation between them. The most recently visited top-level destination is considered the "current" one.
 *
 * The internal state is managed using `LinkedHashMap` to preserve the insertion order of top-level
 * destinations, effectively creating a history of which tabs were visited. The individual back stacks
 * for each tab are `MutableList`s that are observable by Compose.
 *
 * This class is designed to be saved and restored using a `Saver`, for which the `saveState()`
 * method is provided.
 *
 * @param T The type of the navigation key, which must extend [NavKey].
 * @param initialBackStacks A map where each key is a top-level destination and the value is its
 * initial back stack. Used for state restoration.
 * @param initialTopLevelOrder A list representing the historical order of visited top-level
 * destinations. Used for state restoration.
 */
@Stable
class TopLevelBackStack<T : NavKey>(
    initialBackStacks: Map<T, List<T>>,
    initialTopLevelOrder: List<T>
) {
    // We use SnapshotStateMap so Compose detects when tabs are added/removed.
    private val topLevelStacks: SnapshotStateMap<T, MutableList<T>> = mutableStateMapOf()
    // We maintain a separate list to track the order (history) of tabs, since Map doesn't guarantee it.
    private val topLevelOrder: MutableList<T> = mutableStateListOf()

    init {
        // Reconstruct internal state
        initialTopLevelOrder.forEach { key ->
            val stack = initialBackStacks[key] ?: listOf(key)
            topLevelStacks[key] = stack.toMutableStateList()
            topLevelOrder.add(key)
        }
    }

    val currentTopLevelKey: T
        get() = topLevelOrder.last()

    /**
     * Flattens all backstacks into a single list for the navigator.
     * This acts as the source of truth for the displayed content.
     */
    val backStack: List<T>
        get() = topLevelOrder.flatMap { key ->
            topLevelStacks[key] ?: emptyList()
        }

    /**
     * Returns ONLY the backstack of the currently active top-level destination.
     * This is the most efficient way to render if you don't need cross-tab animations.
     * The inactive tabs remain in memory (state) but are not composed.
     */
    val visibleBackStack: List<T>
        get() {
            if (topLevelOrder.isEmpty()) return emptyList()
            val currentKey = currentTopLevelKey
            return topLevelStacks[currentKey] ?: emptyList()
        }

    fun navigateToTopLevel(key: T) {
        if (topLevelOrder.contains(key)) {
            // Move existing tab to the end (make it active)
            topLevelOrder.remove(key)
            topLevelOrder.add(key)
        } else {
            // Create new entry for unvisited tab
            topLevelStacks[key] = mutableStateListOf(key)
            topLevelOrder.add(key)
        }
    }

    fun push(key: T) {
        val currentKey = currentTopLevelKey
        topLevelStacks[currentKey]?.add(key)
    }

    fun pop() {
        val currentKey = currentTopLevelKey
        val currentStack = topLevelStacks[currentKey] ?: return

        if (currentStack.size > 1) {
            currentStack.removeAt(currentStack.lastIndex)
        } else {
            // If it's the last screen in the stack, close the tab and return to the previous one
            if (topLevelOrder.size > 1) {
                topLevelStacks.remove(currentKey)
                topLevelOrder.remove(currentKey)
            }
        }
    }

    /**
     * Extracts the raw state for the Saver to persist.
     */
    fun saveState(): Pair<Map<T, List<T>>, List<T>> {
        return topLevelStacks.toMap().mapValues { it.value.toList() } to topLevelOrder.toList()
    }
}
