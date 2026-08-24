/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.components.card

import android.text.format.Formatter.formatFileSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Deblur
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.components.image.AsyncImage
import com.bobbyesp.docucraft.core.presentation.components.others.Placeholder
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.core.util.DateTime
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.shared.presentation.Measurements
import java.util.UUID

enum class ScannedDocumentCardPosition {
    TOP,
    MIDDLE,
    BOTTOM,
    SINGLE,
}

/** Width of the leading page preview. Kept in one place so the A4 ratio stays authoritative. */
private val ThumbnailWidth = 56.dp

/**
 * A single document in the home list.
 *
 * The supporting line deliberately shows *metadata* (date, page count, size) rather than the
 * user-authored description: the description is usually absent, while these three fields are always
 * present and are exactly what the sort controls operate on. The description lives in the action
 * sheet, where it was authored and where there is room for it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScannedDocumentListItem(
    pdf: ScannedDocument,
    onItemClick: (String) -> Unit,
    onItemLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    position: ScannedDocumentCardPosition = ScannedDocumentCardPosition.SINGLE,
    selected: Boolean = false,
) {
    val shape =
        when (position) {
            ScannedDocumentCardPosition.TOP -> DocucraftShapeDefaults.topListItemShape
            ScannedDocumentCardPosition.MIDDLE -> DocucraftShapeDefaults.middleListItemShape
            ScannedDocumentCardPosition.BOTTOM -> DocucraftShapeDefaults.bottomListItemShape
            ScannedDocumentCardPosition.SINGLE -> DocucraftShapeDefaults.cardShape
        }

    val containerColor =
        if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }

    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    val title = pdf.title ?: pdf.filename
    val metadata = rememberDocumentMetadata(pdf)

    // Aliased because inside a `semantics {}` block the receiver's own `selected` extension would
    // shadow this parameter, and that extension is setter-only — reading it throws at runtime.
    val isSelected = selected

    Surface(
        modifier =
            modifier
                .clip(shape)
                .combinedClickable(
                    role = Role.Button,
                    onClick = { onItemClick(pdf.uuid) },
                    onLongClick = onItemLongClick,
                )
                .semantics { this.selected = isSelected },
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DocumentThumbnail(thumbnail = pdf.thumbnail)

            // One merged node: TalkBack reads the title and metadata as a single sentence rather
            // than announcing the visual "·" separators as content.
            Column(
                modifier =
                    Modifier.weight(1f).clearAndSetSemantics {
                        contentDescription = "$title, ${metadata.spoken}"
                    },
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    // Weight doubles as the non-colour cue for selection.
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = metadata.displayed,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (selected) {
                            LocalContentColor.current
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    maxLines = 1,
                    // Truncating from the tail keeps the date, the most useful field, visible
                    // longest at large font scales.
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(onClick = onItemLongClick) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(id = R.string.more_options),
                )
            }
        }
    }
}

/** The page preview. A plain rounded rectangle at A4 ratio so the scanned page stays legible. */
@Composable
private fun DocumentThumbnail(thumbnail: String?, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .width(ThumbnailWidth)
                .aspectRatio(Measurements.A4_RATIO)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        if (LocalInspectionMode.current) {
            Icon(
                modifier = Modifier.padding(12.dp).fillMaxSize(),
                imageVector = Icons.Rounded.Deblur,
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
                        contentDescription = null,
                        colorful = false,
                    )
                },
            )
        }
    }
}

/**
 * [displayed] uses "·" separators for scanning by eye; [spoken] uses commas so TalkBack reads a
 * natural sentence instead of announcing punctuation.
 */
private data class DocumentMetadata(val displayed: String, val spoken: String)

@Composable
private fun rememberDocumentMetadata(pdf: ScannedDocument): DocumentMetadata {
    val context = LocalContext.current
    val pageCount =
        pluralStringResource(id = R.plurals.doc_n_pages, count = pdf.pageCount, pdf.pageCount)

    return remember(pdf.createdTimestamp, pdf.pageCount, pdf.fileSize, pageCount, context) {
        val date =
            DateTime.formatDate(
                timestampMillis = pdf.createdTimestamp,
                format = DateTime.DateFormat.LOCALIZED_MEDIUM,
            )
        val size = formatFileSize(context, pdf.fileSize)

        DocumentMetadata(
            displayed = listOf(date, pageCount, size).joinToString(separator = " · "),
            spoken = listOf(date, pageCount, size).joinToString(separator = ", "),
        )
    }
}

@Preview
@Composable
private fun ScannedDocumentListItemPreview() {
    DocucraftTheme {
        ScannedDocumentListItem(
            modifier = Modifier,
            pdf =
                ScannedDocument(
                    filename = "Document.pdf",
                    title = "Internet bill — January",
                    description = "This is a sample document",
                    path = "path".toUri(),
                    createdTimestamp = 1630000000000,
                    fileSize = 1_248_576,
                    pageCount = 5,
                    thumbnail = "thumbnail",
                    uuid = UUID.randomUUID().toString(),
                    id = 1,
                ),
            onItemClick = {},
            onItemLongClick = {},
        )
    }
}

@Preview
@Composable
private fun ListScannedDocumentListItemPreview() {
    DocucraftTheme {
        val list =
            List(11) {
                ScannedDocument(
                    filename = "Document $it.pdf",
                    title = "Document $it",
                    description = if (it % 2 == 0) "This is a sample document $it" else null,
                    path = "path".toUri(),
                    createdTimestamp = 1630000000000 + it,
                    fileSize = 1024L * (it + 1) * 37,
                    pageCount = 1 + it,
                    thumbnail = if (it % 3 == 0) "thumbnail" else null,
                    uuid = UUID.randomUUID().toString(),
                    id = 2,
                )
            }
        LazyColumn(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(items = list, key = { _, item -> item.uuid }) { index, item ->
                val position =
                    when {
                        list.size == 1 -> ScannedDocumentCardPosition.SINGLE
                        index == 0 -> ScannedDocumentCardPosition.TOP
                        index == list.lastIndex -> ScannedDocumentCardPosition.BOTTOM
                        else -> ScannedDocumentCardPosition.MIDDLE
                    }

                ScannedDocumentListItem(
                    modifier = Modifier,
                    pdf = item,
                    position = position,
                    selected = index == 2,
                    onItemClick = {},
                    onItemLongClick = {},
                )
            }
        }
    }
}
