/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.components.list

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.common.ScrollHeading
import com.bobbyesp.docucraft.core.presentation.common.ScrollHeadingThreshold
import com.bobbyesp.docucraft.core.presentation.common.ScrollHeadingTracker
import com.bobbyesp.docucraft.core.presentation.components.FrostedMenuGroup
import com.bobbyesp.docucraft.core.presentation.components.HaloDropdownMenuPopup
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.label
import dev.chrisbanes.haze.HazeState

/*
 * What a screen that lists documents has around its list, besides the app bar and the section
 * headers every screen shares (`core/presentation/components/ScreenChrome.kt`): Home, and a folder.
 */

/**
 * Whether the button floating over a list shows its label: not while the list is read downwards,
 * and again once the user heads back up or is at the top.
 *
 * It listens through [nestedScrollConnection], which only watches what the list scrolled and never
 * consumes any of it, and follows where that scroll is heading rather than its last pixel. Derived
 * from the list's last scroll instead, the button changed on any movement at all: it shrank and
 * grew under a finger that was only settling, and grew back by itself after a drag past the end of
 * the list, which scrolls nothing.
 */
@Stable
class FabExpansionState
internal constructor(private val listState: LazyListState, thresholdPx: Float) {

    private val heading = ScrollHeadingTracker(thresholdPx)
    private var isHeadingBack by mutableStateOf(true)

    val isExpanded: Boolean
        get() = isHeadingBack || !listState.canScrollBackward

    val nestedScrollConnection: NestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                when (heading.onScrolled(consumed.y)) {
                    ScrollHeading.Forward -> isHeadingBack = false
                    ScrollHeading.Backward -> isHeadingBack = true
                    null -> Unit
                }
                return Offset.Zero
            }
        }

    /** At the top there is nothing to head back to: leaving it takes the whole threshold again. */
    internal fun onReachedTop() {
        isHeadingBack = true
        heading.reset()
    }
}

@Composable
fun rememberFabExpansionState(listState: LazyListState): FabExpansionState {
    val thresholdPx = with(LocalDensity.current) { ScrollHeadingThreshold.toPx() }
    val state = remember(listState, thresholdPx) { FabExpansionState(listState, thresholdPx) }
    LaunchedEffect(state) {
        snapshotFlow { listState.canScrollBackward }
            .collect { canScrollBackward -> if (!canScrollBackward) state.onReachedTop() }
    }
    return state
}

/**
 * The current order, named on the button itself, and a menu to change it: criteria in one group,
 * direction in the other. A transient popup anchored here, not a destination. Its groups are
 * frosted over the list they open on, and a blur halo lifts the menu where Material would give it a
 * shadow.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SortMenu(
    currentSortOption: SortOption,
    onSortOptionChange: (SortOption) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }

    val ascending = currentSortOption.order == SortOption.Order.ASC
    val arrowRotation by
        animateFloatAsState(
            targetValue = if (ascending) 0f else 180f,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "SortOrderArrow",
        )

    val changeSort = { sortOption: SortOption ->
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
        onSortOptionChange(sortOption)
        expanded = false
    }

    val sortByLabel = stringResource(R.string.sort_by)
    val orderLabel =
        stringResource(if (ascending) R.string.sort_ascending else R.string.sort_descending)
    val criterionLabel = currentSortOption.criteria.label()

    Box(modifier = modifier) {
        TextButton(
            onClick = { expanded = true },
            shapes = ButtonDefaults.shapes(),
            modifier =
                Modifier.semantics {
                    contentDescription = sortByLabel
                    stateDescription = "$criterionLabel, $orderLabel"
                },
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Sort,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(text = criterionLabel)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription = null,
                modifier =
                    Modifier.size(ButtonDefaults.IconSize).graphicsLayer {
                        rotationZ = arrowRotation
                    },
            )
        }

        HaloDropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            hazeState = hazeState,
            // The two groups read as one menu: the halo follows their outer corners.
            shape = MenuDefaults.groupShape(index = 0, count = 1).shape,
        ) {
            val criteria = SortOption.Criteria.entries

            FrostedMenuGroup(
                shapes = MenuDefaults.groupShape(index = 0, count = 2),
                hazeState = hazeState,
            ) {
                criteria.forEachIndexed { index, criterion ->
                    SelectableDropdownMenuItem(
                        selected = criterion == currentSortOption.criteria,
                        onClick = { changeSort(currentSortOption.copy(criteria = criterion)) },
                        text = { Text(text = criterion.label()) },
                        shapes = MenuDefaults.itemShape(index = index, count = criteria.size),
                        selectedLeadingIcon = {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(MenuDefaults.GroupSpacing))

            FrostedMenuGroup(
                shapes = MenuDefaults.groupShape(index = 1, count = 2),
                hazeState = hazeState,
            ) {
                val orders =
                    listOf(
                        SortOption.Order.ASC to
                            (R.string.sort_ascending to Icons.Rounded.ArrowUpward),
                        SortOption.Order.DESC to
                            (R.string.sort_descending to Icons.Rounded.ArrowDownward),
                    )
                orders.forEachIndexed { index, (order, labelAndIcon) ->
                    val (label, icon) = labelAndIcon
                    SelectableDropdownMenuItem(
                        selected = order == currentSortOption.order,
                        onClick = { changeSort(currentSortOption.copy(order = order)) },
                        text = { Text(text = stringResource(label)) },
                        shapes = MenuDefaults.itemShape(index = index, count = orders.size),
                        leadingIcon = { Icon(icon, contentDescription = null) },
                        selectedLeadingIcon = {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                        },
                    )
                }
            }
        }
    }
}
