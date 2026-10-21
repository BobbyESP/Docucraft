/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.runtime.Composable

object DocucraftShapeDefaults {
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val topListItemShape: RoundedCornerShape
        @Composable
        get() =
            RoundedCornerShape(
                topStart = shapes.largeIncreased.topStart,
                topEnd = shapes.largeIncreased.topEnd,
                bottomStart = shapes.extraSmall.bottomStart,
                bottomEnd = shapes.extraSmall.bottomStart,
            )

    val middleListItemShape: RoundedCornerShape
        @Composable get() = RoundedCornerShape(shapes.extraSmall.topStart)

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val bottomListItemShape: RoundedCornerShape
        @Composable
        get() =
            RoundedCornerShape(
                topStart = shapes.extraSmall.topStart,
                topEnd = shapes.extraSmall.topEnd,
                bottomStart = shapes.largeIncreased.bottomStart,
                bottomEnd = shapes.largeIncreased.bottomEnd,
            )

    val independentListItemShape: RoundedCornerShape
        @Composable get() = RoundedCornerShape(shapes.largeIncreased.topStart)

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val cardShape: CornerBasedShape
        @Composable get() = shapes.largeIncreased

    /**
     * The resting shape for the item at [index] of [count] in a grouped list, with the list's
     * pressed, selected, focused and hovered shapes on top.
     *
     * Not [ListItemDefaults.segmentedShapes]: its outer corners are the list token's, a step
     * smaller than the ones above, which every grouped surface in the app shares.
     */
    @Composable
    fun segmentedListItemShapes(index: Int, count: Int): ListItemShapes =
        ListItemDefaults.shapes(
            shape =
                when {
                    count == 1 -> independentListItemShape
                    index == 0 -> topListItemShape
                    index == count - 1 -> bottomListItemShape
                    else -> middleListItemShape
                }
        )
}
