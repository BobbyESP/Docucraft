package com.bobbyesp.docucraft.core.presentation.navigation.backstack

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import com.bobbyesp.docucraft.core.presentation.navigation.Route
import kotlinx.serialization.json.Json

private val serializer = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    allowStructuredMapKeys = true
}

/**
 * A Composable function that remembers a [TopLevelBackStack] instance across recompositions and
 * configuration changes.
 *
 * This function utilizes `rememberSaveable` to persist the state of the back stack. The state,
 * which consists of the navigation stacks for each top-level destination and the order of these
 * destinations, is serialized to JSON strings before being saved to a [android.os.Bundle].
 * When the Composable is recreated (e.g., after a configuration change), the JSON strings are
 * deserialized to restore the back stack to its previous state.
 *
 * The serialization is handled by a custom `Json` instance provided by `App.serializer`, which
 * is configured to handle the `Route` sealed interface.
 *
 * @param startRoute The initial top-level route to be placed in the back stack when it's first created.
 *                   This will be the first and only item in both the stacks map and the order list.
 * @return A remembered instance of [TopLevelBackStack] that is either newly created with the
 *         `startRoute` or restored from the saved state.
 */
@Composable
fun rememberTopLevelBackStack(startRoute: Route): TopLevelBackStack<Route> {
    return rememberSaveable(
        saver = Saver(
            save = { backStack ->
                // Get the raw state: Map<Route, List<Route>> and List<Route> (order)
                val (stacks, order) = backStack.saveState()

                // Serialize to json as a string to save the stack in the Bundle
                val stacksJson = serializer.encodeToString(stacks)
                val orderJson = serializer.encodeToString(order)

                listOf(stacksJson, orderJson)
            },
            restore = { savedList ->
                val stacksJson = savedList[0]
                val orderJson = savedList[1]

                // Restore the stack from the json string
                val stacks = serializer.decodeFromString<Map<Route, List<Route>>>(stacksJson)
                val order = serializer.decodeFromString<List<Route>>(orderJson)

                TopLevelBackStack(stacks, order)
            }
        )
    ) {
        // Initial state in case there is nothing saved in the Bundle
        TopLevelBackStack(
            initialBackStacks = mapOf(startRoute to listOf(startRoute)),
            initialTopLevelOrder = listOf(startRoute)
        )
    }
}
