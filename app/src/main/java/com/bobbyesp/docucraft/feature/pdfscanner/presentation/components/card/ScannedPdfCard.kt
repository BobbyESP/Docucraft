package com.bobbyesp.docucraft.feature.pdfscanner.presentation.components.card

import android.net.Uri
import android.text.format.Formatter.formatFileSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.components.image.AsyncImage
import com.bobbyesp.docucraft.core.presentation.components.others.Placeholder
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import java.util.UUID

@Composable
fun ScannedPdfCard(
    modifier: Modifier = Modifier,
    pdf: ScannedPdf,
    onOpenPdf: (Uri) -> Unit,
    onSavePdf: () -> Unit,
    onSharePdf: (Uri) -> Unit,
    onDeletePdf: (Uri) -> Unit,
    onModifyPdfFields: (String) -> Unit,
) {
    var dropdownMenuExpanded by remember { mutableStateOf(false) }

    Surface(modifier = modifier, onClick = { onOpenPdf(pdf.path) }) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val imageModifier =
                Modifier.height(72.dp)
                    .aspectRatio(1f / 1.414f)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primaryContainer)

            if (pdf.thumbnail != null) {
                AsyncImage(
                    modifier = imageModifier,
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
                            modifier = Modifier.padding(12.dp),
                            imageVector = Icons.AutoMirrored.Rounded.InsertDriveFile,
                            contentDescription = stringResource(id = R.string.file_icon),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    },
                )
            } else {
                Icon(
                    modifier = imageModifier.padding(12.dp),
                    imageVector = Icons.AutoMirrored.Rounded.InsertDriveFile,
                    contentDescription = stringResource(id = R.string.file_icon),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
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
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                modifier = Modifier,
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.End,
            ) {
                IconButton(onClick = { dropdownMenuExpanded = !dropdownMenuExpanded }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(id = R.string.more_options),
                    )
                }

                PdfOptionsDropdown(
                    modifier = Modifier,
                    expanded = dropdownMenuExpanded,
                    scannedPdf = pdf,
                    onDismissDropdown = { dropdownMenuExpanded = false },
                    onSavePdf = { onSavePdf() },
                    onSharePdf = { onSharePdf(pdf.path) },
                    onDeletePdf = { onDeletePdf(pdf.path) },
                    onModifyPdfFields = { onModifyPdfFields(pdf.id) },
                )
            }
        }
    }
}

@Composable
fun PdfOptionsDropdown(
    modifier: Modifier = Modifier,
    scannedPdf: ScannedPdf,
    expanded: Boolean,
    onDismissDropdown: () -> Unit = {},
    onSavePdf: () -> Unit = {},
    onSharePdf: () -> Unit = {},
    onDeletePdf: () -> Unit = {},
    onModifyPdfFields: () -> Unit,
) {
    val context = LocalContext.current
    DropdownMenu(
        modifier = modifier,
        expanded = expanded,
        onDismissRequest = onDismissDropdown
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
private fun PdfOptionsDropdownPrev() {
    DocucraftTheme {
        PdfOptionsDropdown(
            expanded = true,
            scannedPdf =
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
            onSharePdf = {},
            onModifyPdfFields = {},
        )
    }
}

@Preview
@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScannedPdfCardPrev() {
    DocucraftTheme {
        ScannedPdfCard(
            pdf =
                ScannedPdf(
                    filename = "Document.pdf",
                    title = "Document",
                    description =
                        "This is a very very large document description" +
                            " for a sample document to see how the text wraps around the card",
                    path = "path".toUri(),
                    createdTimestamp = 1630000000000,
                    fileSize = 1024,
                    pageCount = 5,
                    thumbnail = "thumbnail",
                    id = UUID.randomUUID().toString(),
                ),
            onOpenPdf = {},
            onSharePdf = {},
            onSavePdf = {},
            onDeletePdf = {},
            onModifyPdfFields = {},
        )
    }
}
