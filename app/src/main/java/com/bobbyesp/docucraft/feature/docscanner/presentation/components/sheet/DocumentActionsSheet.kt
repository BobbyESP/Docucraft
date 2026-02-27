package com.bobbyesp.docucraft.feature.docscanner.presentation.components.sheet

import android.text.format.Formatter.formatFileSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material.icons.rounded.SaveAs
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.components.divider.AnimatedWavyDivider
import com.bobbyesp.docucraft.core.presentation.components.divider.defaults.AnimatedWavyDividerDefaults
import com.bobbyesp.docucraft.core.presentation.components.image.AsyncImage
import com.bobbyesp.docucraft.core.presentation.components.others.GridMenu
import com.bobbyesp.docucraft.core.presentation.components.others.GridMenuItem
import com.bobbyesp.docucraft.core.presentation.components.others.Placeholder
import com.bobbyesp.docucraft.core.presentation.components.others.RoundedTag
import com.bobbyesp.docucraft.core.util.DateTime
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.shared.presentation.Measurements
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
internal enum class ActionImportance {
    PRIMARY,
    SECONDARY,
    DESTRUCTIVE,
}

@Immutable
internal data class DocumentAction(
    val icon: ImageVector,
    val title: Int,
    val importance: ActionImportance,
    val action: () -> Unit,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DocumentActionsContent(
    scannedDocument: ScannedDocument,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onModifyFields: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = rememberDocumentActions(
        onSave = onSave,
        onShare = onShare,
        onModifyFields = onModifyFields,
        onDelete = onDelete,
    )

    Column(modifier = modifier) {
        DocumentHeader(scannedDocument = scannedDocument)

        AnimatedWavyDivider(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            strokeWidth = 4.dp,
            colors = AnimatedWavyDividerDefaults.colors(
                color = MaterialTheme.colorScheme.outlineVariant
            )
        )

        Box(modifier = Modifier.heightIn(min = 120.dp)) {
            DocumentActionsRow(
                options = options,
                onOptionSelect = { it() },
            )
        }
    }
}

@Composable
private fun rememberDocumentActions(
    onSave: () -> Unit,
    onShare: () -> Unit,
    onModifyFields: () -> Unit,
    onDelete: () -> Unit,
): ImmutableList<DocumentAction> = remember {
    persistentListOf(
        DocumentAction(
            icon = Icons.Rounded.SaveAs,
            title = R.string.save,
            importance = ActionImportance.PRIMARY,
            action = onSave,
        ),
        DocumentAction(
            icon = Icons.Rounded.Share,
            title = R.string.share,
            importance = ActionImportance.PRIMARY,
            action = onShare,
        ),
        DocumentAction(
            icon = Icons.Rounded.EditNote,
            title = R.string.edit_fields,
            importance = ActionImportance.SECONDARY,
            action = onModifyFields,
        ),
        DocumentAction(
            icon = Icons.Rounded.DeleteForever,
            title = R.string.delete,
            importance = ActionImportance.DESTRUCTIVE,
            action = onDelete,
        ),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DocumentHeader(
    scannedDocument: ScannedDocument,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DocumentThumbnail(thumbnail = scannedDocument.thumbnail)
        DocumentInfo(scannedDocument = scannedDocument)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Suppress("COMPOSE_UNSTABLE_PARAMETER")
@Composable
private fun DocumentThumbnail(
    thumbnail: Any?,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        modifier = modifier
            .widthIn(max = 120.dp)
            .aspectRatio(Measurements.A4_RATIO)
            .clip(MaterialShapes.Slanted.toShape())
            .background(MaterialTheme.colorScheme.primaryContainer),
        imageModel = thumbnail,
        failure = {
            Placeholder(
                modifier = Modifier.heightIn(min = 48.dp),
                icon = Icons.Rounded.QuestionMark,
                contentDescription = stringResource(id = R.string.file_icon),
                colorful = true,
            )
        },
        loading = {
            Icon(
                modifier = Modifier
                    .padding(12.dp)
                    .heightIn(min = 48.dp),
                imageVector = Icons.AutoMirrored.Rounded.InsertDriveFile,
                contentDescription = stringResource(id = R.string.file_icon),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DocumentInfo(
    scannedDocument: ScannedDocument,
    modifier: Modifier = Modifier,
) {
    val formattedDate = rememberSaveable(scannedDocument.createdTimestamp) {
        DateTime.formatTimestamp(
            timestampMillis = scannedDocument.createdTimestamp,
            format = DateTime.DateFormat.LOCALIZED_MEDIUM,
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = scannedDocument.title ?: scannedDocument.filename,
            style = MaterialTheme.typography.titleLargeEmphasized,
            fontWeight = FontWeight.Bold,
        )

        Text(
            modifier = Modifier.alpha(0.75f),
            text = scannedDocument.description ?: stringResource(id = R.string.no_description),
            style = MaterialTheme.typography.bodyMediumEmphasized,
        )

        DocumentTagsRow(
            fileSize = scannedDocument.fileSize,
            pageCount = scannedDocument.pageCount,
            formattedDate = formattedDate,
        )
    }
}

@Composable
private fun DocumentTagsRow(
    fileSize: Long,
    pageCount: Int,
    formattedDate: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val pageCountLabel = pluralStringResource(
        id = R.plurals.doc_n_pages,
        count = pageCount,
        pageCount,
    )

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        RoundedTag(
            icon = Icons.Rounded.Storage,
            text = formatFileSize(context, fileSize),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimaryContainer),
        )
        RoundedTag(
            icon = Icons.Rounded.FileCopy,
            text = pageCountLabel,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimaryContainer),
        )
        RoundedTag(
            icon = Icons.Rounded.CalendarMonth,
            text = formattedDate,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimaryContainer),
        )
    }
}

@Composable
private inline fun DocumentActionsRow(
    options: ImmutableList<DocumentAction>,
    crossinline onOptionSelect: (() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    GridMenu(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        content = {
            options.forEach { option ->
                GridMenuItem(
                    icon = option.icon,
                    title = option.title,
                    containerColor = {
                        when (option.importance) {
                            ActionImportance.PRIMARY -> MaterialTheme.colorScheme.primary
                            ActionImportance.SECONDARY -> MaterialTheme.colorScheme.secondary
                            ActionImportance.DESTRUCTIVE -> MaterialTheme.colorScheme.error
                        }
                    },
                    enabled = true,
                    span = {
                        when (option.importance) {
                            ActionImportance.PRIMARY -> GridItemSpan(1)
                            ActionImportance.SECONDARY -> GridItemSpan(1)
                            ActionImportance.DESTRUCTIVE -> GridItemSpan(maxCurrentLineSpan)
                        }
                    },
                    onClick = { onOptionSelect(option.action) },
                    modifier = Modifier,
                )
            }
        },
    )
}
