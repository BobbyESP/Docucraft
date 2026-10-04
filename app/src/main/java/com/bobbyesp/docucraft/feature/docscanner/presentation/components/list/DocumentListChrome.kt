/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.components.list

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuGroupShapes
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.components.HaloDropdownMenuPopup
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftBlurDefaults
import com.bobbyesp.docucraft.core.presentation.theme.frosted
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.label
import dev.chrisbanes.haze.HazeState

/*
 * What every screen that lists documents has around its list: Home, and a folder.
 */

/**
 * The expressive large app bar of a screen that lists documents. It takes a container tone once the
 * list scrolls beneath it, eased rather than switched, and is frosted in it: what passes under it
 * stays in view, blurred. Until then nothing is beneath it, and it is the page's own surface.
 *
 * @param hazeState Where the content that scrolls beneath it is recorded.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FrostedLargeTopAppBar(
    title: String,
    isContentScrolled: Boolean,
    scrollBehavior: TopAppBarScrollBehavior,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val scrolledFraction by
        animateFloatAsState(
            targetValue = if (isContentScrolled) 1f else 0f,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "TopBarScrolled",
        )
    val containerColor =
        lerp(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceContainer,
            scrolledFraction,
        )

    LargeFlexibleTopAppBar(
        title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        subtitle =
            subtitle?.let {
                { Text(text = it, maxLines = 1, overflow = TextOverflow.StartEllipsis) }
            },
        modifier =
            modifier.frosted(
                state = hazeState,
                style = DocucraftBlurDefaults.surfaceStyle(containerColor),
            ),
        navigationIcon = navigationIcon,
        actions = actions,
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        scrollBehavior = scrollBehavior,
    )
}

/**
 * The name of a section of a list, with room at its end for what acts on the whole section.
 *
 * @param leading What goes before the name, such as the dot of a tag.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = 20.dp, end = if (trailing != null) 8.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = title.uppercase(),
            style =
                MaterialTheme.typography.labelLargeEmphasized.copy(
                    letterSpacing = 1.25.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                ),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke()
    }
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

/**
 * A menu group frosted over [hazeState]'s content. Its shadow is left to the menu's halo, or kept
 * where there is none.
 *
 * The frost goes around the group, because the group's own modifier lands inside its container. It
 * keeps one shape, hovered or not, so that the frost, clipped to it, always matches.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FrostedMenuGroup(
    shapes: MenuGroupShapes,
    hazeState: HazeState,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = shapes.shape
    Box(
        modifier =
            Modifier.frosted(
                state = hazeState,
                style =
                    DocucraftBlurDefaults.surfaceStyle(MenuDefaults.groupStandardContainerColor),
                shape = shape,
            )
    ) {
        DropdownMenuGroup(
            shapes = MenuGroupShapes(shape = shape, inactiveShape = shape),
            containerColor = Color.Transparent,
            shadowElevation =
                if (DocucraftBlurDefaults.isHaloSupported) 0.dp else MenuDefaults.ShadowElevation,
            content = content,
        )
    }
}
