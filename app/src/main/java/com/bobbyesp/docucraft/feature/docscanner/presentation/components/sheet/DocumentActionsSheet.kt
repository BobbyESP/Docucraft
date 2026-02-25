package com.bobbyesp.docucraft.feature.docscanner.presentation.components.sheet

import android.text.format.Formatter.formatFileSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material.icons.rounded.SaveAs
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.presentation.components.image.AsyncImage
import com.bobbyesp.docucraft.core.presentation.components.others.GridMenu
import com.bobbyesp.docucraft.core.presentation.components.others.GridMenuItem
import com.bobbyesp.docucraft.core.presentation.components.others.Placeholder
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch

@Stable
private enum class ActionImportance {
    PRIMARY,
    SECONDARY,
    DESTRUCTIVE,
}

@Immutable
private data class DocumentAction(
    val icon: ImageVector,
    val title: Int,
    val importance: ActionImportance,
    val action: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DocumentActionsSheet(
    scannedDocument: ScannedDocument,
    onDismissRequest: () -> Unit,
    onSavePdf: () -> Unit,
    onSharePdf: () -> Unit,
    onDeletePdf: () -> Unit,
    onModifyPdfFields: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val hideAndExecute: (() -> Unit) -> Unit = { action ->
        scope
            .launch { sheetState.hide() }
            .invokeOnCompletion {
                if (!sheetState.isVisible) {
                    action()
                    onDismissRequest()
                }
            }
    }

    val options =
        persistentListOf(
            DocumentAction(
                icon = Icons.Rounded.SaveAs,
                title = R.string.save,
                importance = ActionImportance.PRIMARY,
                action = onSavePdf,
            ),
            DocumentAction(
                icon = Icons.Rounded.Share,
                title = R.string.share,
                importance = ActionImportance.PRIMARY,
                action = onSharePdf,
            ),
            DocumentAction(
                icon = Icons.Rounded.EditNote,
                title = R.string.edit_fields,
                importance = ActionImportance.SECONDARY,
                action = onModifyPdfFields,
            ),
            DocumentAction(
                icon = Icons.Rounded.DeleteForever,
                title = R.string.delete,
                importance = ActionImportance.DESTRUCTIVE,
                action = onDeletePdf,
            ),
        )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
        contentWindowInsets = { BottomSheetDefaults.windowInsets },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            scannedDocument.thumbnail?.let {
                val imageModifier =
                    Modifier
                        .widthIn(max = 120.dp)
                        .aspectRatio(0.707f)
                        .clip(MaterialShapes.Slanted.toShape())
                        .background(MaterialTheme.colorScheme.primaryContainer)

                AsyncImage(
                    modifier = imageModifier,
                    imageModel = scannedDocument.thumbnail,
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

            Column(
                modifier = Modifier,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = scannedDocument.title ?: scannedDocument.filename,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text =
                        buildAnnotatedString {
                            append(stringResource(id = R.string.file_size))
                            append(": ")
                            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                            append(formatFileSize(context, scannedDocument.fileSize))
                            pop()
                            append(" • ")
                            append(stringResource(id = R.string.page_count))
                            append(": ")
                            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                            append(scannedDocument.pageCount.toString())
                            pop()
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // File Info Section
        HorizontalDivider(
            modifier =
                Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(MaterialTheme.shapes.medium),
            thickness = 3.dp,
        )

        Box(modifier = Modifier.heightIn(min = 120.dp)) {
            DocumentActionsRow(
                options = options,
                onOptionSelect = hideAndExecute,
                modifier = Modifier,
            )
        }
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
