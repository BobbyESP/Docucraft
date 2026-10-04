/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.screens.preferences.viewer

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
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.model.ViewerDefaults
import com.bobbyesp.docucraft.core.domain.model.ViewerFitMode
import com.bobbyesp.docucraft.core.presentation.components.FrostedLargeTopAppBar
import com.bobbyesp.docucraft.core.presentation.components.settings.SettingSwitch
import com.bobbyesp.docucraft.core.presentation.components.settings.SettingsCategory
import com.bobbyesp.docucraft.core.presentation.components.settings.SettingsItemDefaults
import com.bobbyesp.docucraft.core.presentation.components.settings.settingsContentWidth
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.koin.androidx.compose.koinViewModel

/**
 * The document viewer's defaults (decision D2): a switch, and under it the fit mode and night mode
 * every document opens with. Off, those two stay visible but disabled, so the user can see what the
 * switch would turn on, and documents open with the factory settings.
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

    val current = defaults
    val remembers = remembersReadingPosition
    if (current == null || remembers == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator(modifier = Modifier.size(64.dp))
        }
    } else {
        DocumentViewerSettingsContent(
            defaults = current,
            remembersReadingPosition = remembers,
            onBack = onBack,
            showBackButton = showBackButton,
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
    onBack: () -> Unit,
    onRememberReadingPositionChange: (Boolean) -> Unit,
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

    // A setting that stands outside a category takes the margins a category gives its own.
    val settingModifier =
        Modifier.settingsContentWidth().padding(horizontal = SettingsItemDefaults.HorizontalMargin)

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
                                contentDescription = stringResource(R.string.back),
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
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    end = paddingValues.calculateEndPadding(layoutDirection),
                    bottom = paddingValues.calculateBottomPadding() + 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "remember_position", contentType = "settings_item") {
                SettingSwitch(
                    title = stringResource(R.string.viewer_remember_position),
                    supportingText =
                        stringResource(
                            if (remembersReadingPosition) R.string.viewer_remember_position_on_desc
                            else R.string.viewer_remember_position_off_desc
                        ),
                    icon = Icons.Rounded.Bookmark,
                    isChecked = remembersReadingPosition,
                    onCheckedChange = onRememberReadingPositionChange,
                    modifier = settingModifier,
                )
            }

            item(key = "use_defaults", contentType = "settings_item") {
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
                    modifier = settingModifier,
                )
            }

            item(key = "fit_mode", contentType = "settings_section") {
                FitModeSection(
                    selected = defaults.settings.fitMode,
                    enabled = defaults.enabled,
                    onSelect = onFitModeChange,
                    modifier = Modifier.settingsContentWidth(),
                )
            }

            item(key = "night_mode", contentType = "settings_item") {
                SettingSwitch(
                    title = stringResource(R.string.night_mode),
                    supportingText = stringResource(R.string.night_mode_desc),
                    icon = Icons.Rounded.DarkMode,
                    isChecked = defaults.settings.nightMode,
                    onCheckedChange = onNightModeChange,
                    modifier = settingModifier,
                    enabled = defaults.enabled,
                )
            }

            item(key = "session_note", contentType = "settings_note") {
                Text(
                    text = stringResource(R.string.viewer_session_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // Aligned with the titles of the settings above it, inside their containers.
                    modifier = settingModifier.padding(horizontal = 16.dp).padding(top = 8.dp),
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

    SettingsCategory(title = stringResource(R.string.fit_mode), modifier = modifier) {
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

private val ViewerFitMode.label: Int
    get() =
        when (this) {
            ViewerFitMode.WIDTH -> R.string.fit_mode_width
            ViewerFitMode.HEIGHT -> R.string.fit_mode_height
            ViewerFitMode.BOTH -> R.string.fit_mode_page
            ViewerFitMode.PROPORTIONAL -> R.string.fit_mode_proportional
        }

@Composable
private fun ViewerFitMode.icon(): ImageVector =
    when (this) {
        ViewerFitMode.WIDTH -> ImageVector.vectorResource(R.drawable.fit_page_width)
        ViewerFitMode.HEIGHT -> ImageVector.vectorResource(R.drawable.fit_page_height)
        ViewerFitMode.BOTH -> Icons.Rounded.FitScreen
        ViewerFitMode.PROPORTIONAL -> ImageVector.vectorResource(R.drawable.fit_page)
    }

@PreviewLightDark
@Composable
private fun DocumentViewerSettingsOnPreview() {
    DocucraftTheme {
        DocumentViewerSettingsContent(
            defaults = ViewerDefaults(enabled = true),
            remembersReadingPosition = true,
            onBack = {},
            onRememberReadingPositionChange = {},
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
            onBack = {},
            onRememberReadingPositionChange = {},
            onEnabledChange = {},
            onFitModeChange = {},
            onNightModeChange = {},
        )
    }
}
