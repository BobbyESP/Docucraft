package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home

import androidx.compose.animation.animateContentSize
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
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiAction
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.dialogs.DeleteDocumentDialog
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.dialogs.DeleteDocumentSheet
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.dialogs.EditDocumentDetailsDialog
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.dialogs.EditDocumentDetailsSheet
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.sheet.DocumentSheetUiState
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.sheet.SheetPage

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DocumentDialogWrapper(
    sheetState: DocumentSheetUiState,
    onAction: (HomeUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowSizeClass = LocalWindowWidthState.current
    val isCompact = windowSizeClass == WindowWidthSizeClass.Compact
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { onAction(HomeUiAction.DismissSheet) },
        sheetState = bottomSheetState,
        modifier = modifier,
    ) {
        NavDisplay(
            modifier = Modifier.animateContentSize(
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
            ),
            backStack = if (isCompact) sheetState.pageStack else sheetState.pageStack.take(1),
            onBack = { onAction(HomeUiAction.SheetBack) },
            entryProvider = entryProvider {
                entry<SheetPage.Actions> {
                    val doc = sheetState.activeDocument ?: return@entry
                    DocumentActionsContent(
                        scannedDocument = doc,
                        onSave = { onAction(HomeUiAction.SheetRequestSave) },
                        onShare = { onAction(HomeUiAction.SheetRequestShare) },
                        onDelete = { onAction(HomeUiAction.SheetNavigateToDelete) },
                        onModifyFields = { onAction(HomeUiAction.SheetNavigateToEdit) },
                    )
                }
                entry<SheetPage.Edit> {
                    EditDocumentDetailsSheet(
                        state = sheetState.editUiState,
                        onTitleChange = { onAction(HomeUiAction.SheetUpdateTitle(it)) },
                        onDescriptionChange = { onAction(HomeUiAction.SheetUpdateDescription(it)) },
                        onPopDialog = { onAction(HomeUiAction.SheetBack) },
                        onConfirmEdit = { onAction(HomeUiAction.SheetConfirmEdit) },
                    )
                }
                entry<SheetPage.Delete> {
                    val doc = sheetState.activeDocument ?: return@entry
                    DeleteDocumentSheet(
                        document = doc,
                        onDismiss = { onAction(HomeUiAction.SheetBack) },
                        onConfirm = { onAction(HomeUiAction.SheetConfirmDelete) },
                    )
                }
            },
            transitionSpec = {
                slideInHorizontally(initialOffsetX = { it }) togetherWith
                        slideOutHorizontally(targetOffsetX = { -it })
            },
            popTransitionSpec = {
                slideInHorizontally(initialOffsetX = { -it }) togetherWith
                        slideOutHorizontally(targetOffsetX = { it })
            },
            predictivePopTransitionSpec = {
                slideInHorizontally(initialOffsetX = { -it }) togetherWith
                        slideOutHorizontally(targetOffsetX = { it })
            },
        )
    }

    if (!isCompact) {
        when (sheetState.activePage) {
            is SheetPage.Edit -> {
                EditDocumentDetailsDialog(
                    state = sheetState.editUiState,
                    onTitleChange = { onAction(HomeUiAction.SheetUpdateTitle(it)) },
                    onDescriptionChange = { onAction(HomeUiAction.SheetUpdateDescription(it)) },
                    onDismiss = { onAction(HomeUiAction.SheetBack) },
                    onConfirmEdit = { onAction(HomeUiAction.SheetConfirmEdit) },
                    modifier = Modifier.widthIn(max = 560.dp),
                )
            }
            is SheetPage.Delete -> {
                val doc = sheetState.activeDocument ?: return
                DeleteDocumentDialog(
                    scannedDocument = doc,
                    onDismiss = { onAction(HomeUiAction.SheetBack) },
                    onConfirm = { onAction(HomeUiAction.SheetConfirmDelete) },
                    modifier = Modifier.widthIn(max = 560.dp),
                )
            }
            else -> Unit
        }
    }
}

