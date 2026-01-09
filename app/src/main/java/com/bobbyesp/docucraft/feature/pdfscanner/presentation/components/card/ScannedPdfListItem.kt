package com.bobbyesp.docucraft.feature.pdfscanner.presentation.components.card

import android.net.Uri
import android.text.format.Formatter.formatFileSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material.icons.rounded.SaveAs
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.core.net.toUri
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.components.image.AsyncImage
import com.bobbyesp.docucraft.core.presentation.components.others.Placeholder
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import java.util.UUID

enum class ScannedPdfCardPosition {
    TOP,
    MIDDLE,
    BOTTOM,
    SINGLE
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun ScannedPdfListItem(
    pdf: ScannedPdf,
    onOpenPdf: (Uri) -> Unit,
    onSavePdf: () -> Unit,
    onSharePdf: (Uri) -> Unit,
    onDeletePdf: (String) -> Unit,
    onModifyPdfFields: (String) -> Unit,
    modifier: Modifier = Modifier,
    position: ScannedPdfCardPosition = ScannedPdfCardPosition.SINGLE
) {
    val shape =
        when (position) {
            ScannedPdfCardPosition.TOP -> DocucraftShapeDefaults.topListItemShape
            ScannedPdfCardPosition.MIDDLE -> DocucraftShapeDefaults.middleListItemShape
            ScannedPdfCardPosition.BOTTOM -> DocucraftShapeDefaults.bottomListItemShape
            ScannedPdfCardPosition.SINGLE -> DocucraftShapeDefaults.cardShape
        }

    var dropdownMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier =
            modifier.clip(shape).combinedClickable(
                role = Role.Button,
                onClick = { onOpenPdf(pdf.path) },
                onLongClick = { dropdownMenuExpanded = true },
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val imageModifier =
                Modifier.height(72.dp)
                    .width(51.dp) // Fixed width approx 72/1.414 for A4 ratio
                    .clip(MaterialShapes.Slanted.toShape())
                    .background(MaterialTheme.colorScheme.primaryContainer)

            Box(modifier = imageModifier) {
                if (LocalInspectionMode.current) {
                    Icon(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxSize(),
                        imageVector = Icons.AutoMirrored.Rounded.InsertDriveFile,
                        contentDescription = stringResource(id = R.string.file_icon),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else if (pdf.thumbnail != null) {
                    AsyncImage(
                        modifier = Modifier.fillMaxSize(),
                        imageModel = pdf.thumbnail,
                        failure = {
                            Placeholder(
                                modifier = Modifier.fillMaxSize(),
                                icon = Icons.Rounded.QuestionMark,
                                contentDescription = stringResource(id = R.string.file_icon),
                                colorful = true,
                            )
                        },
                        loading = {
                            Icon(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxSize(),
                                imageVector = Icons.AutoMirrored.Rounded.InsertDriveFile,
                                contentDescription = stringResource(id = R.string.file_icon),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                    )
                } else {
                    Icon(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxSize(),
                        imageVector = Icons.AutoMirrored.Rounded.InsertDriveFile,
                        contentDescription = stringResource(id = R.string.file_icon),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    modifier = Modifier,
                    text = pdf.title ?: pdf.filename,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    modifier = Modifier,
                    text = pdf.description ?: stringResource(id = R.string.no_description),
                    fontWeight = FontWeight.Normal,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { dropdownMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(id = R.string.more_options),
                    )
                }

                PdfOptionsDropdown(
                    expanded = dropdownMenuExpanded,
                    scannedPdf = pdf,
                    onDismissDropdown = { dropdownMenuExpanded = false },
                    onSavePdf = onSavePdf,
                    onSharePdf = { onSharePdf(pdf.path) },
                    onDeletePdf = { onDeletePdf(pdf.id) },
                    onModifyPdfFields = { onModifyPdfFields(pdf.id) },
                )
            }
        }
    }
}

@Composable
fun PdfOptionsDropdown(
    scannedPdf: ScannedPdf,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onModifyPdfFields: () -> Unit = {},
    onDismissDropdown: () -> Unit = {},
    onSavePdf: () -> Unit = {},
    onSharePdf: () -> Unit = {},
    onDeletePdf: () -> Unit = {}
) {
    val context = LocalContext.current
    DropdownMenu(
        modifier = modifier,
        expanded = expanded,
        onDismissRequest = onDismissDropdown,
        properties =
            PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                clippingEnabled = true,
            ),
    ) {
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.SaveAs,
                    contentDescription = stringResource(id = R.string.save_pdf),
                )
            },
            text = { Text(text = stringResource(id = R.string.save)) },
            onClick = onSavePdf,
        )

        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = stringResource(id = R.string.share_pdf),
                )
            },
            text = { Text(text = stringResource(id = R.string.share)) },
            onClick = onSharePdf,
        )

        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.EditNote,
                    contentDescription = stringResource(id = R.string.edit_fields),
                )
            },
            text = { Text(text = stringResource(id = R.string.edit_fields)) },
            onClick = onModifyPdfFields,
        )

        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = stringResource(id = R.string.delete_pdf),
                )
            },
            text = { Text(text = stringResource(id = R.string.delete)) },
            colors =
                MenuDefaults.itemColors(
                    leadingIconColor = MaterialTheme.colorScheme.error,
                    textColor = MaterialTheme.colorScheme.error,
                ),
            onClick = onDeletePdf,
        )

        HorizontalDivider()

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                modifier = Modifier,
                text =
                    buildAnnotatedString {
                        append(stringResource(id = R.string.file_size))
                        append(": ")
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(formatFileSize(context, scannedPdf.fileSize))
                        pop()
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                modifier = Modifier,
                text =
                    buildAnnotatedString {
                        append(stringResource(id = R.string.page_count))
                        append(": ")
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(scannedPdf.pageCount.toString())
                        pop()
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Preview
@Composable
private fun ScannedPdfListItemPrev() {
    DocucraftTheme {
        ScannedPdfListItem(
            modifier = Modifier,
            pdf =
            ScannedPdf(
                filename = "Document.pdf",
                title = "Document",
                description = "This is a sample document",
                path = "path".toUri(),
                createdTimestamp = 1630000000000,
                fileSize = 1024,
                pageCount = 5,
                thumbnail = "thumbnail",
                id = UUID.randomUUID().toString(),
            ),
            onOpenPdf = {},
            onSavePdf = {},
            onSharePdf = {},
            onDeletePdf = {},
            onModifyPdfFields = {},
        )
    }
}

@Preview
@Composable
private fun ListScannedPdfListItemPreview() {
    DocucraftTheme {
        val list =
            List(11) {
                ScannedPdf(
                    filename = "Document $it.pdf",
                    title = "Document $it",
                    description = if (it % 2 == 0) "This is a sample document $it" else null,
                    path = "path".toUri(),
                    createdTimestamp = 1630000000000 + it,
                    fileSize = 1024L * it,
                    pageCount = 5 + it,
                    thumbnail = if (it % 3 == 0) "thumbnail" else null,
                    id = UUID.randomUUID().toString(),
                )
            }
        LazyColumn(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(items = list, key = { _, item -> item.id }) { index, item ->
                val position =
                    when {
                        list.size == 1 -> ScannedPdfCardPosition.SINGLE
                        index == 0 -> ScannedPdfCardPosition.TOP
                        index == list.lastIndex -> ScannedPdfCardPosition.BOTTOM
                        else -> ScannedPdfCardPosition.MIDDLE
                    }

                ScannedPdfListItem(
                    modifier = Modifier,
                    pdf = item,
                    position = position,
                    onOpenPdf = {},
                    onSavePdf = {},
                    onSharePdf = {},
                    onDeletePdf = {},
                    onModifyPdfFields = {},
                )
            }
        }
    }
}
