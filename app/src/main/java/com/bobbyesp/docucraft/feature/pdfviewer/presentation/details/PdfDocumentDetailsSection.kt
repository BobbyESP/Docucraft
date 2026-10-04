/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.details

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.components.overlay.OverlayForm
import com.bobbyesp.docucraft.core.presentation.navigation.Navigator
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.OverlaySceneStrategy
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.core.util.DateTime
import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentOrigin
import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentThumbnail
import com.bobbyesp.docucraft.feature.docscanner.domain.model.FolderIcon
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Tag
import com.bobbyesp.docucraft.feature.docscanner.domain.model.folderIcon
import com.bobbyesp.docucraft.feature.docscanner.domain.model.labelColor
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization.FolderBadge
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.organization.TagDot
import com.bobbyesp.docucraft.feature.pdfviewer.domain.details.LibraryDetails
import com.bobbyesp.docucraft.feature.pdfviewer.domain.details.ViewerDocumentDetails
import com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase.DocumentText
import com.bobbyesp.docucraft.feature.pdfviewer.navigation.PdfDocumentDetails
import com.bobbyesp.docucraft.feature.shared.presentation.DocumentHeroCard
import com.bobbyesp.docucraft.feature.shared.presentation.DocumentHeroFact
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The details of an open document, registered by both hosts that show documents: the app's shell
 * and `PdfViewerActivity`.
 *
 * A destination rather than a sheet the viewer keeps in a boolean, so [OverlaySceneStrategy] picks
 * a sheet or a dialog for the window, back closes it, and it survives rotation and process death
 * like any other entry (`docs/navigation.md`). [OverlayForm] gives it the same heading and close
 * button in either, as every other overlay of the app has.
 */
fun EntryProviderScope<NavKey>.pdfDocumentDetailsSection(navigator: Navigator) {
    entry<PdfDocumentDetails>(metadata = OverlaySceneStrategy.overlay()) { key ->
        val viewModel: PdfDocumentDetailsViewModel = koinViewModel { parametersOf(key.document) }
        val state by viewModel.state.collectAsStateWithLifecycle()

        // This entry, not whatever is on top, which need not be this one.
        LaunchedEffect(state, key) {
            if (state is PdfDocumentDetailsState.Gone) navigator.removeDestination(key)
        }

        val details = (state as? PdfDocumentDetailsState.Ready)?.details ?: return@entry

        PdfDocumentDetailsContent(details = details, onDismiss = navigator::goBack)
    }
}

/**
 * What is known of the document. The document itself first, as the card that introduces it after a
 * scan: its page, its name and description, and how much there is of it. Then what the library
 * knows, for a document it keeps, and what its file says, each a group of rows as Home's lists are.
 *
 * A fact that is not known is left out rather than shown as a dash: a document of another app may
 * have neither a size nor a page count to give. Whether it has text is always said, since "none" is
 * an answer the reader can act on, but as one row among the others: it is one fact of the file, not
 * what the details are about.
 */
@Composable
fun PdfDocumentDetailsContent(
    details: ViewerDocumentDetails,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val library = details.library

    OverlayForm(
        title = stringResource(R.string.document_details),
        icon = Icons.Rounded.Info,
        onDismiss = onDismiss,
        modifier = modifier,
        dismissText = stringResource(R.string.close),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DocumentHeroCard(
                name = details.name,
                thumbnail = library?.thumbnail,
                description = details.description,
                // Where it came from, for a document that came from somewhere the app knows.
                badge =
                    when (library?.origin) {
                        DocumentOrigin.SCAN -> Icons.Rounded.DocumentScanner
                        DocumentOrigin.IMPORT -> Icons.Rounded.SaveAlt
                        null -> null
                    },
            ) {
                details.pageCount?.let { pages ->
                    DocumentHeroFact(
                        icon = Icons.Rounded.FileCopy,
                        text = pluralStringResource(R.plurals.doc_n_pages, pages, pages),
                    )
                }
                details.sizeBytes?.let { size ->
                    DocumentHeroFact(
                        icon = Icons.Rounded.Storage,
                        text = Formatter.formatShortFileSize(context, size),
                    )
                }
                if (library?.isFavorite == true) {
                    DocumentHeroFact(
                        icon = Icons.Rounded.Star,
                        text = stringResource(R.string.favorite),
                    )
                }
            }

            if (library != null) LibraryGroup(library = library)

            FileGroup(details = details)
        }
    }
}

/** Where the user put the document, and when it got here. */
@Composable
private fun LibraryGroup(library: LibraryDetails) {
    val rows = buildList {
        add(
            DetailRow(
                label = stringResource(R.string.folder),
                value = library.folder?.name ?: stringResource(R.string.library),
                leading = {
                    FolderBadge(
                        icon = library.folder?.folderIcon ?: FolderIcon.Default,
                        color = library.folder?.labelColor,
                        size = BadgeSize,
                    )
                },
            )
        )
        if (library.tags.isNotEmpty()) {
            add(
                DetailRow(
                    label = stringResource(R.string.tags),
                    icon = Icons.AutoMirrored.Rounded.Label,
                    content = { TagNames(tags = library.tags) },
                )
            )
        }
        add(
            DetailRow(
                label =
                    stringResource(
                        when (library.origin) {
                            DocumentOrigin.SCAN -> R.string.document_details_scanned
                            DocumentOrigin.IMPORT -> R.string.document_details_imported
                        }
                    ),
                value = formattedDateTime(library.enteredAtEpochMillis),
                icon =
                    when (library.origin) {
                        DocumentOrigin.SCAN -> Icons.Rounded.DocumentScanner
                        DocumentOrigin.IMPORT -> Icons.Rounded.SaveAlt
                    },
            )
        )
        library.modifiedAtEpochMillis?.let { modified ->
            add(
                DetailRow(
                    label = stringResource(R.string.document_details_modified),
                    value = formattedDateTime(modified),
                    icon = Icons.Rounded.Edit,
                )
            )
        }
    }

    DetailGroup(title = stringResource(R.string.document_details_in_library), rows = rows)
}

/**
 * What the file itself says: what it is called, what kind of PDF it is, and whether it has text.
 */
@Composable
private fun FileGroup(details: ViewerDocumentDetails) {
    val rows = buildList {
        details.fileName?.let { fileName ->
            add(
                DetailRow(
                    label = stringResource(R.string.document_details_file_name),
                    value = fileName,
                    icon = Icons.AutoMirrored.Rounded.InsertDriveFile,
                )
            )
        }
        details.pdfVersion?.let { version ->
            add(
                DetailRow(
                    label = stringResource(R.string.document_details_format),
                    value = stringResource(R.string.document_details_pdf_version, version),
                    icon = Icons.Rounded.PictureAsPdf,
                )
            )
        }
        add(
            DetailRow(
                label = stringResource(R.string.document_text),
                value =
                    stringResource(
                        when (details.text) {
                            null -> R.string.document_text_checking
                            DocumentText.Embedded -> R.string.document_text_embedded
                            DocumentText.Recognized -> R.string.document_text_recognized
                            DocumentText.None -> R.string.document_text_none
                            DocumentText.Unsupported -> R.string.document_text_unsupported
                            DocumentText.Unknown -> R.string.document_text_unknown
                        }
                    ),
                icon = Icons.Rounded.TextFields,
                // It takes reading some of its pages: said as being looked into until it is known.
                isWorking = details.text == null,
            )
        )
    }

    DetailGroup(title = stringResource(R.string.document_details_file), rows = rows)
}

@Composable
private fun formattedDateTime(epochMillis: Long): String =
    remember(epochMillis) { DateTime.formatDateTime(epochMillis) }

/**
 * One thing known of the document, as a row of a group.
 *
 * @property value What it is, in words; or [content] for what words alone do not say.
 * @property leading What goes before it instead of [icon] on its round badge.
 * @property isWorking Whether it is still being found out, which the badge shows.
 */
private class DetailRow(
    val label: String,
    val value: String? = null,
    val icon: ImageVector? = null,
    val leading: (@Composable () -> Unit)? = null,
    val content: (@Composable () -> Unit)? = null,
    val isWorking: Boolean = false,
)

/** A named group of rows, joined as the items of a grouped list are. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DetailGroup(title: String, rows: List<DetailRow>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = title.uppercase(),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            style =
                MaterialTheme.typography.labelLargeEmphasized.copy(
                    letterSpacing = 1.25.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                ),
        )
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            rows.forEachIndexed { index, row ->
                DetailRowItem(row = row, shape = groupShape(index = index, count = rows.size))
            }
        }
    }
}

@Composable
private fun groupShape(index: Int, count: Int): Shape =
    when {
        count == 1 -> DocucraftShapeDefaults.independentListItemShape
        index == 0 -> DocucraftShapeDefaults.topListItemShape
        index == count - 1 -> DocucraftShapeDefaults.bottomListItemShape
        else -> DocucraftShapeDefaults.middleListItemShape
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DetailRowItem(row: DetailRow, shape: Shape) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (row.leading != null) {
                row.leading.invoke()
            } else {
                Box(
                    modifier =
                        Modifier.size(BadgeSize)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (row.isWorking) {
                        LoadingIndicator(
                            modifier = Modifier.size(BadgeSize * 0.6f),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    } else if (row.icon != null) {
                        Icon(
                            imageVector = row.icon,
                            contentDescription = null,
                            modifier = Modifier.size(BadgeSize / 2),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (row.content != null) {
                    row.content.invoke()
                } else if (row.value != null) {
                    Text(text = row.value, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

private val BadgeSize = 40.dp

/** The tags of the document, each by its dot and its name, as the review of a scan shows them. */
@Composable
private fun TagNames(tags: List<Tag>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (tag in tags) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TagDot(color = tag.labelColor, size = 8.dp)
                Text(
                    text = tag.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun PdfDocumentDetailsContentPreview() {
    DocucraftTheme {
        Surface {
            PdfDocumentDetailsContent(
                details =
                    ViewerDocumentDetails(
                        name = "Invoice March",
                        description = "Paid on 12/03",
                        pageCount = 3,
                        sizeBytes = 184_320,
                        text = DocumentText.Embedded,
                        fileName = "Scan_20260312_101500.pdf",
                        pdfVersion = "1.7",
                        library =
                            LibraryDetails(
                                origin = DocumentOrigin.SCAN,
                                enteredAtEpochMillis = 1_773_310_500_000,
                                modifiedAtEpochMillis = null,
                                folder = null,
                                tags =
                                    listOf(
                                        Tag("a", "Receipts", color = "amber", homePosition = null),
                                        Tag("b", "Home", color = "teal", homePosition = null),
                                    ),
                                isFavorite = true,
                                thumbnail = DocumentThumbnail("doc", 0L),
                            ),
                    ),
                onDismiss = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun PdfDocumentDetailsExternalPreview() {
    DocucraftTheme {
        Surface {
            PdfDocumentDetailsContent(
                details =
                    ViewerDocumentDetails(
                        name = "timetable.pdf",
                        description = null,
                        pageCount = 12,
                        sizeBytes = 2_408_100,
                        pdfVersion = "1.4",
                    ),
                onDismiss = {},
            )
        }
    }
}
