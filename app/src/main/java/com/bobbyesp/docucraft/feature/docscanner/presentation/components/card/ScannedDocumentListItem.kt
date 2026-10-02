/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.components.card

import android.text.format.Formatter.formatShortFileSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.components.image.AsyncImage
import com.bobbyesp.docucraft.core.presentation.components.others.Placeholder
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.core.util.DateTime
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentThumbnail
import com.bobbyesp.docucraft.feature.docscanner.presentation.preview.DocumentPreviewData
import com.bobbyesp.docucraft.feature.shared.presentation.Measurements
import java.util.UUID

/**
 * A catalogued document, as one segment of Home's grouped list.
 *
 * Built on the expressive [SegmentedListItem], so pressing, selecting and focusing it morph its
 * corners and colors the way every other list in the app does, instead of a surface of our own that
 * had to fake each state.
 *
 * @param shapes where the item sits in the list; see
 *   [DocucraftShapeDefaults.segmentedListItemShapes].
 * @param selected whether this is the document open beside the list, on windows wide enough to show
 *   both.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScannedDocumentListItem(
    pdf: Document.Managed,
    onItemClick: (String) -> Unit,
    onItemLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    shapes: ListItemShapes = DocucraftShapeDefaults.segmentedListItemShapes(index = 0, count = 1),
    selected: Boolean = false,
) {
    SegmentedListItem(
        selected = selected,
        onClick = { onItemClick(pdf.uuid) },
        shapes = shapes,
        modifier = modifier,
        onLongClick = onItemLongClick,
        onLongClickLabel = stringResource(id = R.string.more_options),
        // The thumbnail is taller than two lines of text: top-aligned, a document without a
        // description left its title stranded above an empty band.
        verticalAlignment = Alignment.CenterVertically,
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        leadingContent = { DocumentThumbnail(thumbnail = pdf.thumbnail) },
        supportingContent = { DocumentSummary(pdf = pdf) },
        trailingContent = {
            IconButton(onClick = onItemLongClick, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(id = R.string.more_options),
                )
            }
        },
    ) {
        Text(
            text = pdf.name,
            style = MaterialTheme.typography.bodyLargeEmphasized,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The description, when there is one, above what is known of the document: pages, size and date. A
 * missing description used to take the line with "No description", which told the user nothing and
 * hid the facts that do tell documents apart. Pages or size that are not known are left out rather
 * than shown as zero.
 */
@Composable
private fun DocumentSummary(pdf: Document.Managed, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val pages = pdf.pageCount?.let { pluralStringResource(R.plurals.doc_n_pages, it, it) }
    val facts =
        remember(pdf.sizeBytes, pdf.createdAtEpochMillis, pages) {
            listOfNotNull(
                    pages,
                    pdf.sizeBytes?.let { formatShortFileSize(context, it) },
                    DateTime.formatDate(
                        pdf.createdAtEpochMillis,
                        DateTime.DateFormat.LOCALIZED_MEDIUM,
                    ),
                )
                .joinToString(separator = " · ")
        }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        pdf.description
            ?.takeIf { it.isNotBlank() }
            ?.let { description ->
                Text(text = description, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        Text(
            text = facts,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The first page at the proportions of a page, so a scan reads as a document at a glance. A plain
 * rounded rectangle: a morphing shape would crop the page it is supposed to show.
 */
@Composable
private fun DocumentThumbnail(thumbnail: DocumentThumbnail, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .width(48.dp)
                .aspectRatio(Measurements.A4_RATIO)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (LocalInspectionMode.current) {
            Icon(
                modifier = Modifier.padding(10.dp).fillMaxSize(),
                imageVector = Icons.Rounded.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                imageModel = thumbnail,
                failure = {
                    Placeholder(
                        modifier = Modifier.fillMaxSize(),
                        icon = Icons.Rounded.QuestionMark,
                        contentDescription = stringResource(id = R.string.file_icon),
                        colorful = true,
                    )
                },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ScannedDocumentListItemPreview() {
    DocucraftTheme {
        ScannedDocumentListItem(
            pdf = previewDocument(index = 1, description = "This is a sample document"),
            onItemClick = {},
            onItemLongClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ScannedDocumentListPreview() {
    DocucraftTheme {
        val documents =
            List(5) {
                previewDocument(
                    index = it,
                    description = if (it % 2 == 0) "This is a sample document $it" else null,
                )
            }
        LazyColumn(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            itemsIndexed(items = documents, key = { _, item -> item.uuid }) { index, item ->
                ScannedDocumentListItem(
                    modifier = Modifier.fillMaxWidth(),
                    pdf = item,
                    shapes =
                        DocucraftShapeDefaults.segmentedListItemShapes(
                            index = index,
                            count = documents.size,
                        ),
                    selected = index == 1,
                    onItemClick = {},
                    onItemLongClick = {},
                )
            }
        }
    }
}

private fun previewDocument(index: Int, description: String?) =
    DocumentPreviewData.document(
        uuid = UUID.nameUUIDFromBytes("doc-$index".toByteArray()).toString(),
        title = "Document $index",
        description = description,
        createdAtEpochMillis = 1_758_290_000_000 + index,
        sizeBytes = 184_320L * (index + 1),
        pageCount = 1 + index,
    )
