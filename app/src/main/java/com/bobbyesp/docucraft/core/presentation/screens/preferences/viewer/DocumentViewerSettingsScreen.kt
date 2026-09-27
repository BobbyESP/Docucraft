/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.screens.preferences.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.model.ViewerDefaults
import com.bobbyesp.docucraft.core.domain.model.ViewerFitMode
import com.bobbyesp.docucraft.core.presentation.components.settings.SettingSwitch
import com.bobbyesp.docucraft.core.presentation.screens.preferences.appearance.AppearanceSection
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
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

    when (val current = defaults) {
        null ->
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator(modifier = Modifier.size(64.dp))
            }

        else ->
            DocumentViewerSettingsContent(
                defaults = current,
                onBack = onBack,
                showBackButton = showBackButton,
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
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onFitModeChange: (ViewerFitMode) -> Unit,
    onNightModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.document_viewer)) },
                subtitle = { Text(stringResource(R.string.document_viewer_desc)) },
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
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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
                )
            }

            item(key = "fit_mode", contentType = "settings_section") {
                FitModeSection(
                    selected = defaults.settings.fitMode,
                    enabled = defaults.enabled,
                    onSelect = onFitModeChange,
                )
            }

            item(key = "night_mode", contentType = "settings_item") {
                SettingSwitch(
                    title = stringResource(R.string.night_mode),
                    supportingText = stringResource(R.string.night_mode_desc),
                    icon = Icons.Rounded.DarkMode,
                    isChecked = defaults.settings.nightMode,
                    onCheckedChange = onNightModeChange,
                    enabled = defaults.enabled,
                )
            }

            item(key = "session_note", contentType = "settings_note") {
                Text(
                    text = stringResource(R.string.viewer_session_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun FitModeSection(
    selected: ViewerFitMode,
    enabled: Boolean,
    onSelect: (ViewerFitMode) -> Unit,
) {
    AppearanceSection(
        title = stringResource(R.string.fit_mode),
        modifier = Modifier.alpha(if (enabled) 1f else DisabledAlpha),
    ) {
        Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
            ViewerFitMode.entries.forEach { fitMode ->
                val isSelected = fitMode == selected
                ListItem(
                    modifier =
                        Modifier.selectable(
                            selected = isSelected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(fitMode) },
                        ),
                    headlineContent = {
                        Text(
                            text = stringResource(fitMode.label),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = fitMode.icon(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    // The row is the control; the radio only shows its state.
                    trailingContent = {
                        RadioButton(selected = isSelected, onClick = null, enabled = enabled)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
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

/** Material's opacity for disabled content. */
private const val DisabledAlpha = 0.38f

@PreviewLightDark
@Composable
private fun DocumentViewerSettingsOnPreview() {
    DocucraftTheme {
        DocumentViewerSettingsContent(
            defaults = ViewerDefaults(enabled = true),
            onBack = {},
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
            onBack = {},
            onEnabledChange = {},
            onFitModeChange = {},
            onNightModeChange = {},
        )
    }
}
