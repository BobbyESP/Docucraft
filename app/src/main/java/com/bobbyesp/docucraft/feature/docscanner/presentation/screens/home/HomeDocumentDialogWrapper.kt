package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.bobbyesp.docucraft.core.presentation.common.LocalWindowWidthState
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.sheet.DocumentActionsContent
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.DocumentDialog
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiAction
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.dialogs.DeleteDocumentSheet
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.dialogs.DeleteDocumentDialog
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.dialogs.EditDocumentDetailsSheet
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.dialogs.EditDocumentDetailsDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeDocumentDialogWrapper(
    activeDialogs: List<DocumentDialog>,
    onAction: (HomeUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowSizeClass = LocalWindowWidthState.current
    val isCompact = windowSizeClass == WindowWidthSizeClass.Compact

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { onAction(HomeUiAction.DismissDialogs) },
        sheetState = sheetState,
        modifier = modifier,
    ) {
        NavDisplay(
            modifier = Modifier.animateContentSize(
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
            ),
            backStack = if (isCompact) activeDialogs else activeDialogs.take(1),
            onBack = { onAction(HomeUiAction.PopDialog) },
            entryProvider = entryProvider {
                entry<DocumentDialog.Actions> { key ->
                    DocumentActionsContent(
                        scannedDocument = key.doc,
                        onSavePdf = { onAction(HomeUiAction.SaveDocument(key.doc)) },
                        onSharePdf = { onAction(HomeUiAction.ShareDocument(key.doc.path)) },
                        onDeletePdf = { onAction(HomeUiAction.ShowDeleteConfirmation(key.doc.id)) },
                        onModifyPdfFields = { onAction(HomeUiAction.ShowEditDialog(key.doc.id)) },
                    )
                }
                entry<DocumentDialog.Edit> { key ->
                    EditDocumentDetailsSheet(
                        doc = key.doc,
                        onPopDialog = { onAction(HomeUiAction.PopDialog) },
                        onConfirmEdit = { title, description ->
                            onAction(
                                HomeUiAction.UpdateDocumentFields(
                                    id = key.doc.id,
                                    title = title,
                                    description = description,
                                )
                            )
                        },
                    )
                }
                entry<DocumentDialog.Delete> { key ->
                    DeleteDocumentSheet(
                        document = key.doc,
                        onDismiss = { onAction(HomeUiAction.PopDialog) },
                        onConfirm = { onAction(HomeUiAction.DeleteDocument(key.doc.id)) },
                    )
                }
            },
            transitionSpec = {
                (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it } + fadeOut())
            },
            popTransitionSpec = {
                (slideInHorizontally { -it } + fadeIn()) togetherWith
                        (slideOutHorizontally { it } + fadeOut())
            },
        )
    }

    if (!isCompact) {
        activeDialogs.lastOrNull()?.let { topDialog ->
            when (topDialog) {
                is DocumentDialog.Edit -> EditDocumentDetailsDialog(
                    onDismiss = { onAction(HomeUiAction.PopDialog) },
                    onConfirm = { title, description ->
                        onAction(
                            HomeUiAction.UpdateDocumentFields(
                                id = topDialog.doc.id,
                                title = title,
                                description = description,
                            )
                        )
                    },
                    title = topDialog.doc.title,
                    description = topDialog.doc.description,
                    modifier = Modifier.widthIn(max = 560.dp),
                )

                is DocumentDialog.Delete -> DeleteDocumentDialog(
                    scannedDocument = topDialog.doc,
                    onDismiss = { onAction(HomeUiAction.PopDialog) },
                    onConfirm = { onAction(HomeUiAction.DeleteDocument(topDialog.doc.id)) },
                    modifier = Modifier.widthIn(max = 560.dp),
                )

                is DocumentDialog.Actions -> Unit // Already managed by the sheet
            }
        }
    }
}
