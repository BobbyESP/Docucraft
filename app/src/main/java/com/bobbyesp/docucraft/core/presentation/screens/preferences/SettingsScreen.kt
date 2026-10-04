/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.screens.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.components.settings.SettingSwitch
import com.bobbyesp.docucraft.core.presentation.components.settings.SettingsGroup
import com.bobbyesp.docucraft.core.presentation.components.settings.SettingsItem
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

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
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
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
            LargeFlexibleTopAppBar(
                title = { Text(text = stringResource(R.string.settings)) },
                modifier = Modifier,
                navigationIcon = {
                    IconButton(
                        shape = CircleShape,
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                        onClick = { onBack() },
                        content = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = null,
                            )
                        },
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(32.dp),
        ) {
            item(contentType = "settings_category") {
                Text(
                    text = stringResource(R.string.general),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
            item(contentType = "settings_list") {
                SettingsGroup(modifier = Modifier, items = settings)
            }
            item(contentType = "settings_category") {
                Text(
                    text = stringResource(R.string.documents),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            item(contentType = "settings_item") {
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
                    shapes = DocucraftShapeDefaults.segmentedListItemShapes(index = 0, count = 2),
                )
            }
            item(contentType = "settings_item") {
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
                    modifier = Modifier.padding(top = ListItemDefaults.SegmentedGap),
                    shapes = DocucraftShapeDefaults.segmentedListItemShapes(index = 1, count = 2),
                )
            }
        }
    }
}
