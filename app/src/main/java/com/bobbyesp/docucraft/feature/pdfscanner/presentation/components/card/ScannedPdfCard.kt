package com.bobbyesp.docucraft.feature.pdfscanner.presentation.components.card

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import androidx.core.net.toUri

@Composable
fun ScannedPdfCard(
    modifier: Modifier = Modifier,
    pdf: ScannedPdf,
    onOpenPdf: (Uri) -> Unit,
    onSharePdf: (Uri) -> Unit,
) {
    var dropdownMenuExpanded by remember {
        mutableStateOf(false)
    }

    Surface(
        modifier = modifier,
        onClick = {
            onOpenPdf(pdf.path)
        }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                modifier = Modifier
                    .height(72.dp)
                    .aspectRatio(1f / 1.414f) // A4 vertical aspect ratio
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(12.dp),
                imageVector = Icons.AutoMirrored.Rounded.InsertDriveFile,
                contentDescription = stringResource(id = R.string.file_icon),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    modifier = Modifier,
                    text = pdf.title ?: pdf.filename,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    modifier = Modifier,
                    text = pdf.description ?: stringResource(id = R.string.no_description),
                    fontWeight = FontWeight.Normal,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(
                modifier = Modifier,
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.End
            ) {
                IconButton(
                    onClick = {
                        dropdownMenuExpanded = !dropdownMenuExpanded
                    }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(id = R.string.more_options)
                    )
                }

                PdfOptionsDropdown(
                    modifier = Modifier,
                    expanded = dropdownMenuExpanded,
                    scannedPdf = pdf,
                    onDismissDropdown = {
                        dropdownMenuExpanded = false
                    },
                    onSharePdf = {
                        onSharePdf(pdf.path)
                    }
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
    onSharePdf: () -> Unit
) {
    DropdownMenu(
        modifier = modifier, expanded = expanded, onDismissRequest = onDismissDropdown,
    ) {
        DropdownMenuItem(
            text = {
                Text(text = stringResource(id = R.string.share_pdf))
            },
            onClick = onSharePdf
        )
        HorizontalDivider()
    }
}

@Preview
@Composable
private fun PdfOptionsDropdownPrev() {
    DocucraftTheme {
        PdfOptionsDropdown(
            expanded = true,
            scannedPdf = ScannedPdf(
                filename = "Document.pdf",
                title = "Document",
                description = "This is a sample document",
                path = "path".toUri(),
                createdTimestamp = 1630000000000,
                fileSize = 1024,
                pageCount = 5,
                thumbnail = "thumbnail"
            ),
            onSharePdf = {}
        )
    }
}

@Preview
@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScannedPdfCardPrev() {
    DocucraftTheme {
        ScannedPdfCard(
            pdf = ScannedPdf(
                filename = "Document.pdf",
                title = "Document",
                description = "This is a very very large document description" +
                        " for a sample document to see how the text wraps around the card",
                path = "path".toUri(),
                createdTimestamp = 1630000000000,
                fileSize = 1024,
                pageCount = 5,
                thumbnail = "thumbnail"
            ),
            onOpenPdf = {},
            onSharePdf = {}
        )
    }
}