package com.bobbyesp.docucraft.feature.pdfscanner.presentation.components.card

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.components.image.AsyncImage
import com.bobbyesp.docucraft.core.presentation.components.others.Placeholder
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults.bottomListItemShape
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults.middleListItemShape
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults.topListItemShape
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import java.util.UUID

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NewScannedPdfCard(modifier: Modifier = Modifier, pdf: ScannedPdf, onOpenPdf: (Uri) -> Unit) {
    ListItem(
        modifier =
            modifier.combinedClickable(
                role = Role.Button,
                onClick = { onOpenPdf(pdf.path) },
                onLongClick = { /* TODO: Implement long click action (open bottom sheet) */ },
            ),
        leadingContent = {
            val imageModifier =
                Modifier.height(72.dp)
                    .aspectRatio(1f / 1.414f) // 9:16 aspect ratio
                    .clip(MaterialShapes.Slanted.toShape())
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
        },
        headlineContent = {
            Text(
                modifier = Modifier,
                text = pdf.title ?: pdf.filename,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                modifier = Modifier,
                text = pdf.description ?: stringResource(id = R.string.no_description),
                fontWeight = FontWeight.Normal,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Preview
@Composable
private fun NewScannedPdfCardPrev() {
    NewScannedPdfCard(
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
    ) {}
}

@Preview
@Composable
private fun ListNewScannedPdfCardPrev() {
    val list =
        List(10) {
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
    LazyColumn {
        itemsIndexed(items = list, key = { _, item -> item.id }) { index, item ->
            NewScannedPdfCard(
                modifier =
                    Modifier.clip(
                        when (index) {
                            0 -> topListItemShape
                            list.lastIndex -> bottomListItemShape
                            else -> middleListItemShape
                        }
                    ),
                pdf = item,
            ) {}
        }
    }
}
