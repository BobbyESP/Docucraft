/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.settings.presentation.scanning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.docucraft.core.designsystem.R as DesignsystemR
import com.bobbyesp.docucraft.core.designsystem.components.FrostedLargeTopAppBar
import com.bobbyesp.docucraft.core.designsystem.components.settings.SettingSwitch
import com.bobbyesp.docucraft.core.designsystem.components.settings.SettingsCategory
import com.bobbyesp.docucraft.core.designsystem.components.settings.settingsContentWidth
import com.bobbyesp.docucraft.core.designsystem.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.core.designsystem.theme.DocucraftTheme
import com.bobbyesp.docucraft.feature.settings.R
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScanningSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    viewModel: ScanningSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val current = state
    if (current == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator(modifier = Modifier.size(64.dp))
        }
    } else {
        ScanningSettingsContent(
            state = current,
            onBack = onBack,
            onReviewNewScansChange = viewModel::setReviewNewScans,
            onRecognizeTextInNewDocumentsChange = viewModel::setRecognizeTextInNewDocuments,
            modifier = modifier,
            showBackButton = showBackButton,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScanningSettingsContent(
    state: ScanningSettingsState,
    onBack: () -> Unit,
    onReviewNewScansChange: (Boolean) -> Unit,
    onRecognizeTextInNewDocumentsChange: (Boolean) -> Unit,
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
                title = stringResource(R.string.scanning),
                subtitle = stringResource(R.string.scanning_desc),
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
            item(key = "after_scanning", contentType = "settings_section") {
                SettingsCategory(
                    title = stringResource(R.string.after_scanning),
                    modifier = Modifier.settingsContentWidth(),
                ) {
                    SettingSwitch(
                        title = stringResource(R.string.review_new_scans),
                        supportingText =
                            stringResource(
                                if (state.reviewsNewScans) R.string.review_new_scans_on_desc
                                else R.string.review_new_scans_off_desc
                            ),
                        icon = Icons.Rounded.RateReview,
                        isChecked = state.reviewsNewScans,
                        onCheckedChange = onReviewNewScansChange,
                        modifier = Modifier.fillMaxWidth(),
                        shapes =
                            DocucraftShapeDefaults.segmentedListItemShapes(index = 0, count = 2),
                    )
                    SettingSwitch(
                        title = stringResource(R.string.recognize_text_in_new_documents),
                        supportingText =
                            stringResource(
                                if (state.recognizesTextInNewDocuments) {
                                    R.string.recognize_text_in_new_documents_on_desc
                                } else {
                                    R.string.recognize_text_in_new_documents_off_desc
                                }
                            ),
                        icon = Icons.Rounded.TextFields,
                        isChecked = state.recognizesTextInNewDocuments,
                        onCheckedChange = onRecognizeTextInNewDocumentsChange,
                        modifier = Modifier.fillMaxWidth(),
                        shapes =
                            DocucraftShapeDefaults.segmentedListItemShapes(index = 1, count = 2),
                    )
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ScanningSettingsPreview() {
    DocucraftTheme {
        ScanningSettingsContent(
            state =
                ScanningSettingsState(reviewsNewScans = true, recognizesTextInNewDocuments = false),
            onBack = {},
            onReviewNewScansChange = {},
            onRecognizeTextInNewDocumentsChange = {},
        )
    }
}
