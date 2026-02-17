package com.bobbyesp.docucraft.feature.docscanner.presentation.components.card

import android.net.Uri
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
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
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftShapeDefaults
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import java.util.UUID

enum class ScannedPdfCardPosition {
    TOP,
    MIDDLE,
    BOTTOM,
    SINGLE,
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun ScannedPdfListItem(
    pdf: ScannedDocument,
    onOpenPdf: (Uri) -> Unit,
    onMoreOptionsClick: () -> Unit,
    modifier: Modifier = Modifier,
    position: ScannedPdfCardPosition = ScannedPdfCardPosition.SINGLE,
) {
    val shape =
        when (position) {
            ScannedPdfCardPosition.TOP -> DocucraftShapeDefaults.topListItemShape
            ScannedPdfCardPosition.MIDDLE -> DocucraftShapeDefaults.middleListItemShape
            ScannedPdfCardPosition.BOTTOM -> DocucraftShapeDefaults.bottomListItemShape
            ScannedPdfCardPosition.SINGLE -> DocucraftShapeDefaults.cardShape
        }

    Surface(
        modifier =
            modifier
                .clip(shape)
                .combinedClickable(
                    role = Role.Button,
                    onClick = { onOpenPdf(pdf.path) },
                    onLongClick = onMoreOptionsClick,
                ),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val imageModifier =
                Modifier.widthIn(max = 58.dp)
                    .aspectRatio(0.707f)
                    .clip(MaterialShapes.Slanted.toShape())
                    .background(MaterialTheme.colorScheme.primaryContainer)

            Box(modifier = imageModifier) {
                if (LocalInspectionMode.current) {
                    Icon(
                        modifier = Modifier.padding(12.dp).fillMaxSize(),
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
                                modifier = Modifier.padding(12.dp).fillMaxSize(),
                                imageVector = Icons.AutoMirrored.Rounded.InsertDriveFile,
                                contentDescription = stringResource(id = R.string.file_icon),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                    )
                } else {
                    Icon(
                        modifier = Modifier.padding(12.dp).fillMaxSize(),
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(onClick = onMoreOptionsClick) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(id = R.string.more_options),
                )
            }
        }
    }
}

@Preview
@Composable
private fun ScannedPdfListItemPreview() {
    DocucraftTheme {
        ScannedPdfListItem(
            modifier = Modifier,
            pdf =
                ScannedDocument(
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
            onMoreOptionsClick = {},
        )
    }
}

@Preview
@Composable
private fun ListScannedPdfListItemPreview() {
    DocucraftTheme {
        val list =
            List(11) {
                ScannedDocument(
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
            verticalArrangement = Arrangement.spacedBy(2.dp),
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
                    onMoreOptionsClick = {},
                )
            }
        }
    }
}
