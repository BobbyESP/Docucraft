/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.screens.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.components.FrostedLargeTopAppBar
import com.bobbyesp.docucraft.core.presentation.components.settings.SettingSwitch
import com.bobbyesp.docucraft.core.presentation.components.settings.SettingsCategory
import com.bobbyesp.docucraft.core.presentation.components.settings.SettingsGroup
import com.bobbyesp.docucraft.core.presentation.components.settings.SettingsItem
import com.bobbyesp.docucraft.core.presentation.components.settings.settingsContentWidth
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

/**
 * The root of settings, built from what Home is built from so that it reads as the same app: its
 * app bar, its section headers and its grouped lists. Top to bottom: the screens that hold more
 * settings; the few that are set right here, each a switch; and which app, in which version, all of
 * this belongs to.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onOpenAppearance: () -> Unit,
    onOpenDocumentViewer: () -> Unit,
    recognizesTextInNewDocuments: Boolean,
    onRecognizeTextInNewDocumentsChange: (Boolean) -> Unit,
    reviewsNewScans: Boolean,
    onReviewNewScansChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current

    // The list, recorded for the app bar to frost once it scrolls beneath it.
    val hazeState = rememberHazeState()

    val settings: PersistentList<SettingsItem> =
        persistentListOf(
            SettingsItem(
                title = stringResource(R.string.appearance),
                supportingText = stringResource(R.string.appearance_desc),
                icon = Icons.Rounded.ColorLens,
                onClick = onOpenAppearance,
            ),
            SettingsItem(
                title = stringResource(R.string.document_viewer),
                supportingText = stringResource(R.string.document_viewer_desc),
                icon = Icons.Rounded.Description,
                onClick = onOpenDocumentViewer,
            ),
        )

    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FrostedLargeTopAppBar(
                title = stringResource(R.string.settings),
                isContentScrolled = listState.canScrollBackward,
                scrollBehavior = scrollBehavior,
                hazeState = hazeState,
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            state = listState,
            // Padded rather than inset, so the list scrolls beneath the app bar that frosts it.
            contentPadding =
                PaddingValues(
                    start = padding.calculateStartPadding(layoutDirection),
                    top = padding.calculateTopPadding(),
                    end = padding.calculateEndPadding(layoutDirection),
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "general", contentType = "settings_section") {
                SettingsCategory(
                    title = stringResource(R.string.general),
                    modifier = Modifier.settingsContentWidth(),
                ) {
                    SettingsGroup(items = settings)
                }
            }
            item(key = "documents", contentType = "settings_section") {
                SettingsCategory(
                    title = stringResource(R.string.documents),
                    modifier = Modifier.settingsContentWidth(),
                ) {
                    SettingSwitch(
                        title = stringResource(R.string.recognize_text_in_new_documents),
                        supportingText =
                            stringResource(
                                if (recognizesTextInNewDocuments) {
                                    R.string.recognize_text_in_new_documents_on_desc
                                } else {
                                    R.string.recognize_text_in_new_documents_off_desc
                                }
                            ),
                        icon = Icons.Rounded.TextFields,
                        isChecked = recognizesTextInNewDocuments,
                        onCheckedChange = onRecognizeTextInNewDocumentsChange,
                        modifier = Modifier.fillMaxWidth(),
                        shapes =
                            DocucraftShapeDefaults.segmentedListItemShapes(index = 0, count = 2),
                    )
                    SettingSwitch(
                        title = stringResource(R.string.review_new_scans),
                        supportingText =
                            stringResource(
                                if (reviewsNewScans) R.string.review_new_scans_on_desc
                                else R.string.review_new_scans_off_desc
                            ),
                        icon = Icons.Rounded.RateReview,
                        isChecked = reviewsNewScans,
                        onCheckedChange = onReviewNewScansChange,
                        modifier = Modifier.fillMaxWidth(),
                        shapes =
                            DocucraftShapeDefaults.segmentedListItemShapes(index = 1, count = 2),
                    )
                }
            }
            item(key = "about", contentType = "settings_about") {
                AppSignature(modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
            }
        }
    }
}

/**
 * The app's icon, name and installed version, closing the list. Not a setting and not a row:
 * nothing here is tapped, so it has no container, only the icon on one of the expressive shapes the
 * app's empty screens carry.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppSignature(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // The installed package says which version this is; a preview has none to ask.
    val versionName =
        remember(context) {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }
                .getOrNull()
        }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier =
                Modifier.padding(bottom = 12.dp)
                    .size(AppIconSize)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            // The launcher's own foreground, in the theme's colors as a themed icon is. Drawn
            // larger than its container, as the launcher does: only the middle two thirds of an
            // adaptive icon's layer are meant to be seen.
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.requiredSize(AppIconSize * AdaptiveIconLayerRatio),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMediumEmphasized,
        )
        if (versionName != null) {
            Text(
                text = stringResource(R.string.version_format, versionName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val AppIconSize = 96.dp

/** An adaptive icon's layer is 108dp, of which a launcher shows 72dp. */
private const val AdaptiveIconLayerRatio = 108f / 72f

@PreviewLightDark
@Composable
private fun SettingsScreenPreview() {
    DocucraftTheme {
        SettingsScreen(
            onOpenAppearance = {},
            onOpenDocumentViewer = {},
            recognizesTextInNewDocuments = false,
            onRecognizeTextInNewDocumentsChange = {},
            reviewsNewScans = true,
            onReviewNewScansChange = {},
            onBack = {},
        )
    }
}
