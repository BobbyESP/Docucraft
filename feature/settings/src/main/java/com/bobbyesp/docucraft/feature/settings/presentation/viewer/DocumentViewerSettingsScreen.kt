/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.settings.presentation.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.docucraft.core.designsystem.R as DesignsystemR
import com.bobbyesp.docucraft.core.designsystem.components.FrostedLargeTopAppBar
import com.bobbyesp.docucraft.core.designsystem.components.settings.SettingSwitch
import com.bobbyesp.docucraft.core.designsystem.components.settings.SettingsCategory
import com.bobbyesp.docucraft.core.designsystem.components.settings.SettingsItemDefaults
import com.bobbyesp.docucraft.core.designsystem.components.settings.settingsContentWidth
import com.bobbyesp.docucraft.core.designsystem.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.core.designsystem.theme.DocucraftTheme
import com.bobbyesp.docucraft.core.domain.model.SearchArrival
import com.bobbyesp.docucraft.core.domain.model.ViewerDefaults
import com.bobbyesp.docucraft.core.domain.model.ViewerFitMode
import com.bobbyesp.docucraft.feature.settings.R
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.koin.androidx.compose.koinViewModel

/**
 * How documents open: where they start, what a search they were found by does, and the display
 * defaults of decision D2. With the defaults off, night mode and the fit modes stay visible but
 * disabled, so the user sees what the switch would turn on.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DocumentViewerSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    viewModel: DocumentViewerSettingsViewModel = koinViewModel(),
) {
    val defaults by viewModel.defaults.collectAsStateWithLifecycle()
    val remembersReadingPosition by viewModel.remembersReadingPosition.collectAsStateWithLifecycle()
    val searchArrival by viewModel.searchArrival.collectAsStateWithLifecycle()

    val current = defaults
    val remembers = remembersReadingPosition
    val arrival = searchArrival
    if (current == null || remembers == null || arrival == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator(modifier = Modifier.size(64.dp))
        }
    } else {
        DocumentViewerSettingsContent(
            defaults = current,
            remembersReadingPosition = remembers,
            searchArrival = arrival,
            onBack = onBack,
            showBackButton = showBackButton,
            onSearchArrivalChange = viewModel::setSearchArrival,
            onRememberReadingPositionChange = viewModel::setRememberReadingPosition,
            onEnabledChange = viewModel::setEnabled,
            onFitModeChange = viewModel::setFitMode,
            onNightModeChange = viewModel::setNightMode,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DocumentViewerSettingsContent(
    defaults: ViewerDefaults,
    remembersReadingPosition: Boolean,
    searchArrival: SearchArrival,
    onBack: () -> Unit,
    onRememberReadingPositionChange: (Boolean) -> Unit,
    onSearchArrivalChange: (SearchArrival) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onFitModeChange: (ViewerFitMode) -> Unit,
    onNightModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
) {
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current

    // The list, recorded for the app bar to frost once it scrolls beneath it.
    val hazeState = rememberHazeState()

    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FrostedLargeTopAppBar(
                title = stringResource(R.string.document_viewer),
                subtitle = stringResource(R.string.document_viewer_desc),
                isContentScrolled = listState.canScrollBackward,
                scrollBehavior = scrollBehavior,
                hazeState = hazeState,
                navigationIcon = {
                    // Beside the settings list there is already a way back on screen.
                    if (showBackButton) {
                        IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(DesignsystemR.string.back),
                            )
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            state = listState,
            // Padded rather than inset, so the list scrolls beneath the app bar that frosts it.
            contentPadding =
                PaddingValues(
                    start = paddingValues.calculateStartPadding(layoutDirection),
                    top = paddingValues.calculateTopPadding(),
                    end = paddingValues.calculateEndPadding(layoutDirection),
                    bottom = paddingValues.calculateBottomPadding() + 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "opening", contentType = "settings_section") {
                SettingsCategory(
                    title = stringResource(R.string.viewer_opening),
                    modifier = Modifier.settingsContentWidth(),
                ) {
                    SettingSwitch(
                        title = stringResource(R.string.viewer_remember_position),
                        supportingText =
                            stringResource(
                                if (remembersReadingPosition) {
                                    R.string.viewer_remember_position_on_desc
                                } else {
                                    R.string.viewer_remember_position_off_desc
                                }
                            ),
                        icon = Icons.Rounded.Bookmark,
                        isChecked = remembersReadingPosition,
                        onCheckedChange = onRememberReadingPositionChange,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item(key = "search_arrival", contentType = "settings_section") {
                SearchArrivalSection(
                    selected = searchArrival,
                    onSelect = onSearchArrivalChange,
                    modifier = Modifier.settingsContentWidth(),
                )
            }

            item(key = "default_display", contentType = "settings_section") {
                SettingsCategory(
                    title = stringResource(R.string.viewer_default_display),
                    modifier = Modifier.settingsContentWidth(),
                ) {
                    SettingSwitch(
                        title = stringResource(R.string.viewer_use_defaults),
                        supportingText =
                            stringResource(
                                if (defaults.enabled) R.string.viewer_use_defaults_on_desc
                                else R.string.viewer_use_defaults_off_desc
                            ),
                        icon = Icons.Rounded.Tune,
                        isChecked = defaults.enabled,
                        onCheckedChange = onEnabledChange,
                        modifier = Modifier.fillMaxWidth(),
                        shapes =
                            DocucraftShapeDefaults.segmentedListItemShapes(index = 0, count = 2),
                    )
                    SettingSwitch(
                        title = stringResource(DesignsystemR.string.night_mode),
                        supportingText = stringResource(R.string.night_mode_desc),
                        icon = Icons.Rounded.DarkMode,
                        isChecked = defaults.settings.nightMode,
                        onCheckedChange = onNightModeChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = defaults.enabled,
                        shapes =
                            DocucraftShapeDefaults.segmentedListItemShapes(index = 1, count = 2),
                    )
                }
            }

            item(key = "fit_mode", contentType = "settings_section") {
                FitModeSection(
                    selected = defaults.settings.fitMode,
                    enabled = defaults.enabled,
                    onSelect = onFitModeChange,
                    modifier = Modifier.settingsContentWidth(),
                )
            }

            item(key = "session_note", contentType = "settings_note") {
                Text(
                    text = stringResource(R.string.viewer_session_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // Aligned with the titles of the settings above it, inside their containers.
                    modifier =
                        Modifier.settingsContentWidth()
                            .padding(horizontal = SettingsItemDefaults.HorizontalMargin + 16.dp)
                            .padding(top = 8.dp),
                )
            }
        }
    }
}

/**
 * One selectable item per fit mode. The chosen one takes the list's selected color and shape,
 * animated by the item; disabled, each keeps its container and dims its content, so the choice that
 * would apply stays readable.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FitModeSection(
    selected: ViewerFitMode,
    enabled: Boolean,
    onSelect: (ViewerFitMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fitModes = ViewerFitMode.entries

    SettingsCategory(title = stringResource(DesignsystemR.string.fit_mode), modifier = modifier) {
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            fitModes.forEachIndexed { index, fitMode ->
                val isSelected = fitMode == selected
                SegmentedListItem(
                    selected = isSelected,
                    onClick = { onSelect(fitMode) },
                    shapes =
                        DocucraftShapeDefaults.segmentedListItemShapes(
                            index = index,
                            count = fitModes.size,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    leadingContent = {
                        Icon(imageVector = fitMode.icon(), contentDescription = null)
                    },
                    // The item is the radio button to accessibility services; this one only shows
                    // its state.
                    trailingContent = {
                        RadioButton(selected = isSelected, onClick = null, enabled = enabled)
                    },
                    colors = SettingsItemDefaults.colors(),
                ) {
                    Text(
                        text = stringResource(fitMode.label),
                        style = MaterialTheme.typography.bodyLargeEmphasized,
                    )
                }
            }
        }
    }
}

/** One selectable item per [SearchArrival], each with what it does under its name. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchArrivalSection(
    selected: SearchArrival,
    onSelect: (SearchArrival) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = SearchArrival.entries

    SettingsCategory(title = stringResource(R.string.search_arrival), modifier = modifier) {
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = option == selected
                SegmentedListItem(
                    selected = isSelected,
                    onClick = { onSelect(option) },
                    shapes =
                        DocucraftShapeDefaults.segmentedListItemShapes(
                            index = index,
                            count = options.size,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                    trailingContent = { RadioButton(selected = isSelected, onClick = null) },
                    supportingContent = { Text(text = stringResource(option.description)) },
                    colors = SettingsItemDefaults.colors(),
                ) {
                    Text(
                        text = stringResource(option.label),
                        style = MaterialTheme.typography.bodyLargeEmphasized,
                    )
                }
            }
        }
    }
}

private val SearchArrival.label: Int
    get() =
        when (this) {
            SearchArrival.HIGHLIGHT -> R.string.search_arrival_highlight
            SearchArrival.OPEN_FIND_BAR -> R.string.search_arrival_find_bar
            SearchArrival.NOTHING -> R.string.search_arrival_nothing
        }

private val SearchArrival.description: Int
    get() =
        when (this) {
            SearchArrival.HIGHLIGHT -> R.string.search_arrival_highlight_desc
            SearchArrival.OPEN_FIND_BAR -> R.string.search_arrival_find_bar_desc
            SearchArrival.NOTHING -> R.string.search_arrival_nothing_desc
        }

private val ViewerFitMode.label: Int
    get() =
        when (this) {
            ViewerFitMode.WIDTH -> DesignsystemR.string.fit_mode_width
            ViewerFitMode.HEIGHT -> DesignsystemR.string.fit_mode_height
            ViewerFitMode.BOTH -> DesignsystemR.string.fit_mode_page
            ViewerFitMode.PROPORTIONAL -> DesignsystemR.string.fit_mode_proportional
        }

@Composable
private fun ViewerFitMode.icon(): ImageVector =
    when (this) {
        ViewerFitMode.WIDTH -> ImageVector.vectorResource(DesignsystemR.drawable.fit_page_width)
        ViewerFitMode.HEIGHT -> ImageVector.vectorResource(DesignsystemR.drawable.fit_page_height)
        ViewerFitMode.BOTH -> Icons.Rounded.FitScreen
        ViewerFitMode.PROPORTIONAL -> ImageVector.vectorResource(DesignsystemR.drawable.fit_page)
    }

@PreviewLightDark
@Composable
private fun DocumentViewerSettingsOnPreview() {
    DocucraftTheme {
        DocumentViewerSettingsContent(
            defaults = ViewerDefaults(enabled = true),
            remembersReadingPosition = true,
            searchArrival = SearchArrival.HIGHLIGHT,
            onBack = {},
            onRememberReadingPositionChange = {},
            onSearchArrivalChange = {},
            onEnabledChange = {},
            onFitModeChange = {},
            onNightModeChange = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DocumentViewerSettingsOffPreview() {
    DocucraftTheme {
        DocumentViewerSettingsContent(
            defaults = ViewerDefaults(enabled = false),
            remembersReadingPosition = false,
            searchArrival = SearchArrival.OPEN_FIND_BAR,
            onBack = {},
            onRememberReadingPositionChange = {},
            onSearchArrivalChange = {},
            onEnabledChange = {},
            onFitModeChange = {},
            onNightModeChange = {},
        )
    }
}
