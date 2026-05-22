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
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.text.font.FontFamily
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
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
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

enum class TypographyCategory {
    DISPLAY,
    TITLE,
    BODY,
    LABEL,
    MONOSPACE
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
    var activeTypographyCategory by rememberSaveable { mutableStateOf<TypographyCategory?>(null) }

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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
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
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Seed Color
                            ListItem(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(DocucraftShapeDefaults.topListItemShape)
                                    .clickable { showColorPicker = true },
                                headlineContent = {
                                    Text(
                                        text = stringResource(R.string.seed_color),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                supportingContent = {
                                    Text(seedColorHex, style = MaterialTheme.typography.bodyMedium)
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent
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

                            HorizontalDivider(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Palette Style
                            Text(
                                text = stringResource(R.string.palette_style),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            PaletteStylePicker(
                                selectedStyle = uiState.paletteStyle,
                                seedColor = seedColor,
                                isDark = isDark,
                                isAmoled = uiState.isHighContrastModeEnabled,
                                onStyleSelect = onPaletteStyleChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TypographyCategoryRow(
                            categoryName = stringResource(R.string.typography_display),
                            categoryDesc = stringResource(R.string.typography_display_desc),
                            selectedFont = uiState.displayFont,
                            onClick = { activeTypographyCategory = TypographyCategory.DISPLAY },
                            modifier = Modifier.clip(DocucraftShapeDefaults.topListItemShape)
                        )

                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        TypographyCategoryRow(
                            categoryName = stringResource(R.string.typography_title),
                            categoryDesc = stringResource(R.string.typography_title_desc),
                            selectedFont = uiState.titleFont,
                            onClick = { activeTypographyCategory = TypographyCategory.TITLE },
                            modifier = Modifier.clip(DocucraftShapeDefaults.middleListItemShape)
                        )

                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        TypographyCategoryRow(
                            categoryName = stringResource(R.string.typography_body),
                            categoryDesc = stringResource(R.string.typography_body_desc),
                            selectedFont = uiState.bodyFont,
                            onClick = { activeTypographyCategory = TypographyCategory.BODY },
                            modifier = Modifier.clip(DocucraftShapeDefaults.middleListItemShape)
                        )

                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        TypographyCategoryRow(
                            categoryName = stringResource(R.string.typography_label),
                            categoryDesc = stringResource(R.string.typography_label_desc),
                            selectedFont = uiState.labelFont,
                            onClick = { activeTypographyCategory = TypographyCategory.LABEL },
                            modifier = Modifier.clip(DocucraftShapeDefaults.middleListItemShape)
                        )

                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        TypographyCategoryRow(
                            categoryName = stringResource(R.string.typography_monospace),
                            categoryDesc = stringResource(R.string.typography_monospace_desc),
                            selectedFont = uiState.monospaceFont,
                            onClick = { activeTypographyCategory = TypographyCategory.MONOSPACE },
                            modifier = Modifier.clip(DocucraftShapeDefaults.bottomListItemShape)
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

    activeTypographyCategory?.let { category ->
        val title = when (category) {
            TypographyCategory.DISPLAY -> stringResource(R.string.typography_display)
            TypographyCategory.TITLE -> stringResource(R.string.typography_title)
            TypographyCategory.BODY -> stringResource(R.string.typography_body)
            TypographyCategory.LABEL -> stringResource(R.string.typography_label)
            TypographyCategory.MONOSPACE -> stringResource(R.string.typography_monospace)
        }
        val desc = when (category) {
            TypographyCategory.DISPLAY -> stringResource(R.string.typography_display_desc)
            TypographyCategory.TITLE -> stringResource(R.string.typography_title_desc)
            TypographyCategory.BODY -> stringResource(R.string.typography_body_desc)
            TypographyCategory.LABEL -> stringResource(R.string.typography_label_desc)
            TypographyCategory.MONOSPACE -> stringResource(R.string.typography_monospace_desc)
        }
        val selectedFont = when (category) {
            TypographyCategory.DISPLAY -> uiState.displayFont
            TypographyCategory.TITLE -> uiState.titleFont
            TypographyCategory.BODY -> uiState.bodyFont
            TypographyCategory.LABEL -> uiState.labelFont
            TypographyCategory.MONOSPACE -> uiState.monospaceFont
        }
        val onFontSelect: (FontConfig) -> Unit = { font ->
            when (category) {
                TypographyCategory.DISPLAY -> onDisplayFontChange(font)
                TypographyCategory.TITLE -> onTitleFontChange(font)
                TypographyCategory.BODY -> onBodyFontChange(font)
                TypographyCategory.LABEL -> onLabelFontChange(font)
                TypographyCategory.MONOSPACE -> onMonospaceFontChange(font)
            }
        }
        val previewText = when (category) {
            TypographyCategory.DISPLAY -> "Docucraft Scanner"
            TypographyCategory.TITLE -> "Scanned Documents"
            TypographyCategory.BODY -> "This document was processed using Docucraft with advanced layout intelligence."
            TypographyCategory.LABEL -> "CONFIRM EDIT"
            TypographyCategory.MONOSPACE -> "ID: 46F1-37FB-AC5A (60 chars)"
        }
        val previewStyle = when (category) {
            TypographyCategory.DISPLAY -> MaterialTheme.typography.headlineMedium
            TypographyCategory.TITLE -> MaterialTheme.typography.titleMedium
            TypographyCategory.BODY -> MaterialTheme.typography.bodyMedium
            TypographyCategory.LABEL -> MaterialTheme.typography.labelLarge
            TypographyCategory.MONOSPACE -> MaterialTheme.typography.bodySmall
        }

        FontSelectionDialog(
            title = title,
            description = desc,
            selectedFont = selectedFont,
            onFontSelect = onFontSelect,
            previewText = previewText,
            previewStyle = previewStyle,
            onDismiss = { activeTypographyCategory = null }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppearanceSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(DocucraftShapeDefaults.cardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
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

@Composable
fun TypographyCategoryRow(
    categoryName: String,
    categoryDesc: String,
    selectedFont: FontConfig,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fontFamily = remember(selectedFont) { selectedFont.toFontFamily() }

    ListItem(
        modifier = modifier
            .clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = categoryName,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = categoryDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Active: ${selectedFont.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.TextFields,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
        },
        trailingContent = {
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aa",
                    fontFamily = fontFamily,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontSelectionDialog(
    title: String,
    description: String,
    selectedFont: FontConfig,
    onFontSelect: (FontConfig) -> Unit,
    previewText: String,
    previewStyle: TextStyle,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.confirm))
            }
        },
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Live preview card at the top
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "PREVIEW",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        val fontFamily = remember(selectedFont) { selectedFont.toFontFamily() }
                        Text(
                            text = previewText,
                            style = previewStyle.copy(fontFamily = fontFamily),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                // Scrollable list of fonts below
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(FontConfig.entries) { fontConfig ->
                        val isSelected = selectedFont == fontConfig
                        val fontFamily = remember(fontConfig) { fontConfig.toFontFamily() }
                        
                        ListItem(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { onFontSelect(fontConfig) },
                            headlineContent = {
                                Text(
                                    text = fontConfig.name,
                                    fontFamily = fontFamily,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Aa",
                                        fontFamily = fontFamily,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            trailingContent = {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onFontSelect(fontConfig) }
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                },
                                headlineColor = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        )
                    }
                }
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier
    )
}
