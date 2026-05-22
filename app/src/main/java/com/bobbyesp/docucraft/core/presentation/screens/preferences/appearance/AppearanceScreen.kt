package com.bobbyesp.docucraft.core.presentation.screens.preferences.appearance

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.model.FontConfig
import com.bobbyesp.docucraft.core.domain.model.PaletteStyleConfig
import com.bobbyesp.docucraft.core.domain.model.ThemeConfig
import com.bobbyesp.docucraft.core.domain.model.UserPreferences
import com.bobbyesp.docucraft.core.presentation.components.ColorPickerDialog
import com.bobbyesp.docucraft.core.presentation.components.settings.PaletteStylePicker
import com.bobbyesp.docucraft.core.presentation.components.settings.SettingSwitch
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.core.presentation.theme.isDarkTheme
import com.bobbyesp.docucraft.core.presentation.theme.isDynamicColoringSupported
import com.bobbyesp.docucraft.core.presentation.theme.toFontFamily
import org.koin.androidx.compose.koinViewModel

private val SeedColorHexFormat =
    HexFormat {
        upperCase = true
        number {
            prefix = "#"
        }
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppearanceViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val emphasizedEasing = remember { CubicBezierEasing(0.2f, 0.0f, 0f, 1.0f) }

    AnimatedContent(
        targetState = uiState,
        transitionSpec = {
            (fadeIn(animationSpec = tween(500, easing = emphasizedEasing)) +
                    scaleIn(
                        initialScale = 0.92f,
                        animationSpec = tween(500, easing = emphasizedEasing)
                    ))
                .togetherWith(fadeOut(animationSpec = tween(200, easing = emphasizedEasing)))
        },
        modifier = Modifier.fillMaxSize(),
        label = "AppearanceScreenTransition",
        contentKey = { it is AppearanceUiState.Success }
    ) { state ->
        when (state) {
            AppearanceUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator(
                        modifier = Modifier.size(64.dp),
                    )
                }
            }

            is AppearanceUiState.Success -> {
                AppearanceScreenContent(
                    uiState = state.preferences,
                    onBack = onBack,
                    modifier = modifier,
                    onThemeConfigChange = viewModel::updateThemeConfig,
                    onDynamicColoringChange = viewModel::updateDynamicColoring,
                    onThemeSeedColorChange = viewModel::updateThemeSeedColor,
                    onPaletteStyleChange = viewModel::updatePaletteStyle,
                    onHighContrastModeChange = viewModel::updateHighContrastMode,
                    onDisplayFontChange = viewModel::updateDisplayFont,
                    onTitleFontChange = viewModel::updateTitleFont,
                    onBodyFontChange = viewModel::updateBodyFont,
                    onLabelFontChange = viewModel::updateLabelFont,
                    onMonospaceFontChange = viewModel::updateMonospaceFont
                )
            }
        }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun AppearanceScreenContent(
    uiState: UserPreferences,
    onBack: () -> Unit,
    onThemeConfigChange: (ThemeConfig) -> Unit,
    onDynamicColoringChange: (Boolean) -> Unit,
    onThemeSeedColorChange: (Int) -> Unit,
    onPaletteStyleChange: (PaletteStyleConfig) -> Unit,
    onHighContrastModeChange: (Boolean) -> Unit,
    onDisplayFontChange: (FontConfig) -> Unit,
    onTitleFontChange: (FontConfig) -> Unit,
    onBodyFontChange: (FontConfig) -> Unit,
    onLabelFontChange: (FontConfig) -> Unit,
    onMonospaceFontChange: (FontConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val isDark = uiState.themeConfig.isDarkTheme()

    var showColorPicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.appearance)) },
                subtitle = { Text(stringResource(R.string.appearance_desc)) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Theme Selection
            item(key = "theme_selection", contentType = "settings_section") {
                AppearanceSection(title = stringResource(R.string.theme)) {
                    val systemLabel = stringResource(R.string.system)
                    val lightLabel = stringResource(R.string.light)
                    val darkLabel = stringResource(R.string.dark)

                    ButtonGroup(
                        overflowIndicator = {},
                        horizontalArrangement = Arrangement.spacedBy(
                            ButtonGroupDefaults.ConnectedSpaceBetween
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ThemeConfig.entries.forEach { config ->
                            val isSelected = uiState.themeConfig == config
                            val label = when (config) {
                                ThemeConfig.FOLLOW_SYSTEM -> systemLabel
                                ThemeConfig.LIGHT -> lightLabel
                                ThemeConfig.DARK -> darkLabel
                            }
                            val imageVector = when (config) {
                                ThemeConfig.FOLLOW_SYSTEM -> Icons.Rounded.SettingsSuggest
                                ThemeConfig.LIGHT -> Icons.Rounded.LightMode
                                ThemeConfig.DARK -> Icons.Rounded.DarkMode
                            }

                            toggleableItem(
                                checked = isSelected,
                                onCheckedChange = { if (it) onThemeConfigChange(config) },
                                label = label,
                                icon = {
                                    Icon(
                                        imageVector = imageVector,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                weight = 1f,
                            )
                        }
                    }
                }
            }

            // Dynamic Coloring
            if (isDynamicColoringSupported()) {
                item(key = "dynamic_coloring", contentType = "settings_item") {
                    SettingSwitch(
                        title = stringResource(R.string.dynamic_coloring),
                        supportingText = stringResource(R.string.dynamic_coloring_desc),
                        icon = Icons.Rounded.ColorLens,
                        isChecked = uiState.useDynamicColoring,
                        onCheckedChange = onDynamicColoringChange
                    )
                }
            }

            // Custom Color Settings (if dynamic coloring is disabled or not supported)
            if (!uiState.useDynamicColoring || !isDynamicColoringSupported()) {
                item(key = "custom_colors", contentType = "settings_section") {
                    val seedColorHex = remember(uiState.themeSeedColor) {
                        uiState.themeSeedColor.toHexString(SeedColorHexFormat)
                    }

                    val seedColor = remember(uiState.themeSeedColor) {
                        Color(uiState.themeSeedColor)
                    }

                    AppearanceSection(title = stringResource(R.string.custom_colors)) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Seed Color
                            ListItem(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.largeIncreased)
                                    .clickable { showColorPicker = true },
                                headlineContent = {
                                    Text(
                                        text = stringResource(R.string.seed_color),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                supportingContent = {
                                    Text(seedColorHex, style = MaterialTheme.typography.bodyMedium)
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(
                                        8.dp
                                    )
                                ),
                                trailingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(seedColor)
                                            .clickable { showColorPicker = true }
                                    )
                                }
                            )

                            // Palette Style
                            Text(
                                text = stringResource(R.string.palette_style),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            PaletteStylePicker(
                                selectedStyle = uiState.paletteStyle,
                                seedColor = seedColor,
                                isDark = isDark,
                                isAmoled = uiState.isHighContrastModeEnabled,
                                onStyleSelect = onPaletteStyleChange,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }

                item(key = "high_contrast", contentType = "settings_item") {
                    SettingSwitch(
                        title = stringResource(R.string.high_contrast),
                        supportingText = stringResource(R.string.high_contrast_desc),
                        icon = Icons.Rounded.Contrast,
                        isChecked = uiState.isHighContrastModeEnabled,
                        onCheckedChange = onHighContrastModeChange
                    )
                }
            }

            // Typography Selection (Redesigned)
            item(key = "typography_selection", contentType = "settings_section") {
                AppearanceSection(title = stringResource(R.string.typography)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TypographyCategorySelector(
                            categoryName = stringResource(R.string.typography_display),
                            categoryDesc = stringResource(R.string.typography_display_desc),
                            selectedFont = uiState.displayFont,
                            onFontSelect = onDisplayFontChange,
                            previewText = "Docucraft Scanner",
                            previewStyle = MaterialTheme.typography.headlineMedium
                        )

                        TypographyCategorySelector(
                            categoryName = stringResource(R.string.typography_title),
                            categoryDesc = stringResource(R.string.typography_title_desc),
                            selectedFont = uiState.titleFont,
                            onFontSelect = onTitleFontChange,
                            previewText = "Scanned Documents",
                            previewStyle = MaterialTheme.typography.titleMedium
                        )

                        TypographyCategorySelector(
                            categoryName = stringResource(R.string.typography_body),
                            categoryDesc = stringResource(R.string.typography_body_desc),
                            selectedFont = uiState.bodyFont,
                            onFontSelect = onBodyFontChange,
                            previewText = "This document was processed using Docucraft with advanced layout intelligence.",
                            previewStyle = MaterialTheme.typography.bodyMedium
                        )

                        TypographyCategorySelector(
                            categoryName = stringResource(R.string.typography_label),
                            categoryDesc = stringResource(R.string.typography_label_desc),
                            selectedFont = uiState.labelFont,
                            onFontSelect = onLabelFontChange,
                            previewText = "CONFIRM EDIT",
                            previewStyle = MaterialTheme.typography.labelLarge
                        )

                        TypographyCategorySelector(
                            categoryName = stringResource(R.string.typography_monospace),
                            categoryDesc = stringResource(R.string.typography_monospace_desc),
                            selectedFont = uiState.monospaceFont,
                            onFontSelect = onMonospaceFontChange,
                            previewText = "ID: 46F1-37FB-AC5A (60 chars)",
                            previewStyle = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = Color(uiState.themeSeedColor),
            onColorSelected = { onThemeSeedColorChange(it.toArgb()) },
            onDismiss = { showColorPicker = false }
        )
    }
}

@Composable
fun AppearanceSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}

@PreviewLightDark
@Composable
private fun AppearanceScreenPreview() {
    DocucraftTheme {
        AppearanceScreenContent(
            uiState = UserPreferences(),
            onBack = {},
            onThemeConfigChange = {},
            onDynamicColoringChange = {},
            onThemeSeedColorChange = {},
            onPaletteStyleChange = {},
            onHighContrastModeChange = {},
            onDisplayFontChange = {},
            onTitleFontChange = {},
            onBodyFontChange = {},
            onLabelFontChange = {},
            onMonospaceFontChange = {}
        )
    }
}

@PreviewLightDark
@Composable
private fun AppearanceScreenNoDynamicColorPreview() {
    DocucraftTheme {
        AppearanceScreenContent(
            uiState = UserPreferences(
                useDynamicColoring = false
            ),
            onBack = {},
            onThemeConfigChange = {},
            onDynamicColoringChange = {},
            onThemeSeedColorChange = {},
            onPaletteStyleChange = {},
            onHighContrastModeChange = {},
            onDisplayFontChange = {},
            onTitleFontChange = {},
            onBodyFontChange = {},
            onLabelFontChange = {},
            onMonospaceFontChange = {}
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TypographyCategorySelector(
    categoryName: String,
    categoryDesc: String,
    selectedFont: FontConfig,
    onFontSelect: (FontConfig) -> Unit,
    previewText: String,
    previewStyle: TextStyle,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column {
            Text(
                text = categoryName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = categoryDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Font Preview Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
                .padding(12.dp)
        ) {
            val fontFamily = remember(selectedFont) { selectedFont.toFontFamily() }
            Text(
                text = previewText,
                style = previewStyle.copy(fontFamily = fontFamily)
            )
        }

        // Horizontal scroll list of fonts
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(FontConfig.entries) { fontConfig ->
                val isSelected = selectedFont == fontConfig
                val fontFamily = remember(fontConfig) { fontConfig.toFontFamily() }
                ToggleButton(
                    checked = isSelected,
                    onCheckedChange = { if (it) onFontSelect(fontConfig) },
                    colors = ToggleButtonDefaults.tonalToggleButtonColors()
                ) {
                    Text(
                        text = fontConfig.name,
                        fontFamily = fontFamily,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
