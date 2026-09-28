/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.screens.preferences.appearance

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
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
import androidx.compose.ui.text.TextStyle
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
import com.bobbyesp.docucraft.core.presentation.components.settings.SettingsCategory
import com.bobbyesp.docucraft.core.presentation.components.settings.SettingsItemDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.core.presentation.theme.isDarkTheme
import com.bobbyesp.docucraft.core.presentation.theme.isDynamicColoringSupported
import com.bobbyesp.docucraft.core.presentation.theme.toFontFamily
import com.bobbyesp.docucraft.core.util.animateItemWith
import com.bobbyesp.docucraft.core.util.contentRevealTransform
import org.koin.androidx.compose.koinViewModel

private val SeedColorHexFormat = HexFormat {
    upperCase = true
    number { prefix = "#" }
}

/**
 * The type roles the user can pick a font for, with everything the screen shows about each: its
 * name, what it is used for, and a sample set in the role's own style.
 */
enum class TypographyCategory(
    @StringRes val title: Int,
    @StringRes val description: Int,
    val sampleText: String,
) {
    DISPLAY(R.string.typography_display, R.string.typography_display_desc, "Docucraft Scanner"),
    TITLE(R.string.typography_title, R.string.typography_title_desc, "Scanned Documents"),
    BODY(
        R.string.typography_body,
        R.string.typography_body_desc,
        "This document was processed using Docucraft with advanced layout intelligence.",
    ),
    LABEL(R.string.typography_label, R.string.typography_label_desc, "CONFIRM EDIT"),
    MONOSPACE(
        R.string.typography_monospace,
        R.string.typography_monospace_desc,
        "ID: 46F1-37FB-AC5A (60 chars)",
    );

    fun fontIn(preferences: UserPreferences): FontConfig =
        when (this) {
            DISPLAY -> preferences.displayFont
            TITLE -> preferences.titleFont
            BODY -> preferences.bodyFont
            LABEL -> preferences.labelFont
            MONOSPACE -> preferences.monospaceFont
        }

    val sampleStyle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() =
            when (this) {
                DISPLAY -> MaterialTheme.typography.headlineMedium
                TITLE -> MaterialTheme.typography.titleMedium
                BODY -> MaterialTheme.typography.bodyMedium
                LABEL -> MaterialTheme.typography.labelLarge
                MONOSPACE -> MaterialTheme.typography.bodySmall
            }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    viewModel: AppearanceViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val motionScheme = MaterialTheme.motionScheme

    AnimatedContent(
        targetState = uiState,
        modifier = modifier.fillMaxSize(),
        transitionSpec = { motionScheme.contentRevealTransform() },
        // Only arriving from the loading state is a transition; each preference saved is not.
        contentKey = { it is AppearanceUiState.Success },
        label = "AppearanceScreen",
    ) { state ->
        when (state) {
            AppearanceUiState.Loading ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator(modifier = Modifier.size(64.dp))
                }

            is AppearanceUiState.Success ->
                AppearanceScreenContent(
                    preferences = state.preferences,
                    onBack = onBack,
                    showBackButton = showBackButton,
                    onThemeConfigChange = viewModel::updateThemeConfig,
                    onDynamicColoringChange = viewModel::updateDynamicColoring,
                    onThemeSeedColorChange = viewModel::updateThemeSeedColor,
                    onPaletteStyleChange = viewModel::updatePaletteStyle,
                    onHighContrastModeChange = viewModel::updateHighContrastMode,
                    onFontChange = { category, font ->
                        when (category) {
                            TypographyCategory.DISPLAY -> viewModel.updateDisplayFont(font)
                            TypographyCategory.TITLE -> viewModel.updateTitleFont(font)
                            TypographyCategory.BODY -> viewModel.updateBodyFont(font)
                            TypographyCategory.LABEL -> viewModel.updateLabelFont(font)
                            TypographyCategory.MONOSPACE -> viewModel.updateMonospaceFont(font)
                        }
                    },
                )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppearanceScreenContent(
    preferences: UserPreferences,
    onBack: () -> Unit,
    onThemeConfigChange: (ThemeConfig) -> Unit,
    onDynamicColoringChange: (Boolean) -> Unit,
    onThemeSeedColorChange: (Int) -> Unit,
    onPaletteStyleChange: (PaletteStyleConfig) -> Unit,
    onHighContrastModeChange: (Boolean) -> Unit,
    onFontChange: (TypographyCategory, FontConfig) -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val motionScheme = MaterialTheme.motionScheme
    val showsCustomColors = !preferences.useDynamicColoring || !isDynamicColoringSupported()

    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    var editedCategory by rememberSaveable { mutableStateOf<TypographyCategory?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.appearance)) },
                subtitle = { Text(stringResource(R.string.appearance_desc)) },
                navigationIcon = {
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
        // Switching dynamic color on or off adds or removes whole sections: they fade, and the
        // ones below glide to their new place rather than jump.
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "theme", contentType = "settings_section") {
                ThemeSection(
                    selected = preferences.themeConfig,
                    onSelect = onThemeConfigChange,
                    modifier = animateItemWith(motionScheme),
                )
            }

            if (isDynamicColoringSupported()) {
                item(key = "dynamic_coloring", contentType = "settings_item") {
                    SettingSwitch(
                        title = stringResource(R.string.dynamic_coloring),
                        supportingText = stringResource(R.string.dynamic_coloring_desc),
                        icon = Icons.Rounded.ColorLens,
                        isChecked = preferences.useDynamicColoring,
                        onCheckedChange = onDynamicColoringChange,
                        modifier = animateItemWith(motionScheme),
                    )
                }
            }

            if (showsCustomColors) {
                item(key = "custom_colors", contentType = "settings_section") {
                    CustomColorsSection(
                        seedColor = preferences.themeSeedColor,
                        paletteStyle = preferences.paletteStyle,
                        isDark = preferences.themeConfig.isDarkTheme(),
                        isHighContrast = preferences.isHighContrastModeEnabled,
                        onSeedColorClick = { showColorPicker = true },
                        onPaletteStyleChange = onPaletteStyleChange,
                        onHighContrastChange = onHighContrastModeChange,
                        modifier = animateItemWith(motionScheme),
                    )
                }
            }

            item(key = "typography", contentType = "settings_section") {
                TypographySection(
                    preferences = preferences,
                    onCategoryClick = { editedCategory = it },
                    modifier = animateItemWith(motionScheme),
                )
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = Color(preferences.themeSeedColor),
            onColorSelected = { onThemeSeedColorChange(it.toArgb()) },
            onDismiss = { showColorPicker = false },
        )
    }

    editedCategory?.let { category ->
        FontSelectionDialog(
            category = category,
            selectedFont = category.fontIn(preferences),
            onFontSelect = { onFontChange(category, it) },
            onDismiss = { editedCategory = null },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeSection(
    selected: ThemeConfig,
    onSelect: (ThemeConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels =
        mapOf(
            ThemeConfig.FOLLOW_SYSTEM to stringResource(R.string.system),
            ThemeConfig.LIGHT to stringResource(R.string.light),
            ThemeConfig.DARK to stringResource(R.string.dark),
        )

    SettingsCategory(title = stringResource(R.string.theme), modifier = modifier) {
        // The connected group morphs the pressed and the checked button on its own.
        ButtonGroup(
            overflowIndicator = {},
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ThemeConfig.entries.forEach { config ->
                val icon =
                    when (config) {
                        ThemeConfig.FOLLOW_SYSTEM -> Icons.Rounded.SettingsSuggest
                        ThemeConfig.LIGHT -> Icons.Rounded.LightMode
                        ThemeConfig.DARK -> Icons.Rounded.DarkMode
                    }

                toggleableItem(
                    checked = selected == config,
                    onCheckedChange = { if (it) onSelect(config) },
                    label = labels.getValue(config),
                    icon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    weight = 1f,
                )
            }
        }
    }
}

/**
 * The seed color, the palette built from it and its high-contrast variant, as one segmented group:
 * the seed opens the picker, and the palettes preview what each style makes of it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CustomColorsSection(
    seedColor: Int,
    paletteStyle: PaletteStyleConfig,
    isDark: Boolean,
    isHighContrast: Boolean,
    onSeedColorClick: () -> Unit,
    onPaletteStyleChange: (PaletteStyleConfig) -> Unit,
    onHighContrastChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val seedColorHex = remember(seedColor) { seedColor.toHexString(SeedColorHexFormat) }
    // Only the swatch eases into a newly picked color. The palettes are generated from the seed,
    // and generating them on every frame of the fade would be wasted work.
    val swatchColor by
        animateColorAsState(
            targetValue = Color(seedColor),
            animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
            label = "SeedColorSwatch",
        )

    SettingsCategory(title = stringResource(R.string.custom_colors), modifier = modifier) {
        SegmentedListItem(
            onClick = onSeedColorClick,
            shapes = DocucraftShapeDefaults.segmentedListItemShapes(index = 0, count = 3),
            modifier = Modifier.fillMaxWidth(),
            supportingContent = { Text(seedColorHex) },
            trailingContent = {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(swatchColor))
            },
            colors = SettingsItemDefaults.colors(),
        ) {
            Text(
                text = stringResource(R.string.seed_color),
                style = MaterialTheme.typography.bodyLargeEmphasized,
            )
        }

        // Not a list item: it holds its own targets, and a clickable row around them would claim
        // their touches.
        Surface(
            shape = DocucraftShapeDefaults.middleListItemShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.palette_style),
                    style = MaterialTheme.typography.bodyLargeEmphasized,
                )
                PaletteStylePicker(
                    selectedStyle = paletteStyle,
                    seedColor = Color(seedColor),
                    isDark = isDark,
                    isAmoled = isHighContrast,
                    onStyleSelect = onPaletteStyleChange,
                )
            }
        }

        SettingSwitch(
            title = stringResource(R.string.high_contrast),
            supportingText = stringResource(R.string.high_contrast_desc),
            icon = Icons.Rounded.Contrast,
            isChecked = isHighContrast,
            onCheckedChange = onHighContrastChange,
            shapes = DocucraftShapeDefaults.segmentedListItemShapes(index = 2, count = 3),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TypographySection(
    preferences: UserPreferences,
    onCategoryClick: (TypographyCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val categories = TypographyCategory.entries

    SettingsCategory(title = stringResource(R.string.typography), modifier = modifier) {
        categories.forEachIndexed { index, category ->
            TypographyCategoryItem(
                category = category,
                font = category.fontIn(preferences),
                shapes =
                    DocucraftShapeDefaults.segmentedListItemShapes(
                        index = index,
                        count = categories.size,
                    ),
                onClick = { onCategoryClick(category) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** One type role, led by a sample of its current font. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TypographyCategoryItem(
    category: TypographyCategory,
    font: FontConfig,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        leadingContent = { FontSample(font) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = stringResource(category.description))
                Text(
                    text = stringResource(R.string.active_font, font.name),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        colors = SettingsItemDefaults.colors(),
    ) {
        Text(
            text = stringResource(category.title),
            style = MaterialTheme.typography.bodyLargeEmphasized,
        )
    }
}

/**
 * "Aa" in [font] on a tonal disc, the same disc the settings list puts its icons on. A new font
 * grows in over the old one, so the change is seen even behind the dialog's scrim.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FontSample(font: FontConfig, modifier: Modifier = Modifier) {
    val motionScheme = MaterialTheme.motionScheme

    Surface(
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        AnimatedContent(
            targetState = font,
            transitionSpec = { motionScheme.contentRevealTransform() },
            contentAlignment = Alignment.Center,
            label = "FontSample",
        ) { shownFont ->
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Aa",
                    fontFamily = remember(shownFont) { shownFont.toFontFamily() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FontSelectionDialog(
    category: TypographyCategory,
    selectedFont: FontConfig,
    onFontSelect: (FontConfig) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fonts = FontConfig.entries

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.confirm))
            }
        },
        title = { Text(stringResource(category.title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(category.description))

                FontPreview(category = category, font = selectedFont)

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    itemsIndexed(fonts, key = { _, font -> font.name }) { index, font ->
                        FontOption(
                            font = font,
                            selected = font == selectedFont,
                            shapes =
                                DocucraftShapeDefaults.segmentedListItemShapes(
                                    index = index,
                                    count = fonts.size,
                                ),
                            onClick = { onFontSelect(font) },
                        )
                    }
                }
            }
        },
        modifier = modifier,
    )
}

/**
 * The category's sample in [font]. Picking another font grows the new sample in over the old one,
 * and the card follows the text's new size instead of snapping to it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FontPreview(
    category: TypographyCategory,
    font: FontConfig,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val sampleStyle = category.sampleStyle

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.font_preview),
                style = MaterialTheme.typography.labelSmallEmphasized,
            )

            AnimatedContent(
                targetState = font,
                transitionSpec = {
                    motionScheme.contentRevealTransform() using
                        SizeTransform(
                            sizeAnimationSpec = { _, _ -> motionScheme.defaultSpatialSpec() }
                        )
                },
                label = "FontPreview",
            ) { shownFont ->
                Text(
                    text = category.sampleText,
                    style =
                        sampleStyle.copy(
                            fontFamily = remember(shownFont) { shownFont.toFontFamily() }
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * A font to pick, named in itself. The selected one takes the list's selected color and shape, both
 * animated by the item.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FontOption(
    font: FontConfig,
    selected: Boolean,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = shapes,
        modifier = modifier.fillMaxWidth(),
        // The item is the radio button to accessibility services; this one only shows it.
        trailingContent = { RadioButton(selected = selected, onClick = null) },
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
            ),
    ) {
        Text(text = font.name, fontFamily = remember(font) { font.toFontFamily() })
    }
}

@PreviewLightDark
@Composable
private fun AppearanceScreenPreview() {
    DocucraftTheme { AppearanceScreenPreviewContent(UserPreferences()) }
}

@PreviewLightDark
@Composable
private fun AppearanceScreenNoDynamicColorPreview() {
    DocucraftTheme { AppearanceScreenPreviewContent(UserPreferences(useDynamicColoring = false)) }
}

@Composable
private fun AppearanceScreenPreviewContent(preferences: UserPreferences) {
    AppearanceScreenContent(
        preferences = preferences,
        onBack = {},
        onThemeConfigChange = {},
        onDynamicColoringChange = {},
        onThemeSeedColorChange = {},
        onPaletteStyleChange = {},
        onHighContrastModeChange = {},
        onFontChange = { _, _ -> },
    )
}
