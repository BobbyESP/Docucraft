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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.bobbyesp.docucraft.core.presentation.common.LocalWindowWidthState
import com.bobbyesp.docucraft.feature.docscanner.presentation.components.sheet.DocumentActionsContent
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiAction
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.dialogs.DeleteDocumentDialog
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.dialogs.DeleteDocumentSheet
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.dialogs.EditDocumentDetailsDialog
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.dialogs.EditDocumentDetailsSheet
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.sheet.DocumentSheetAction
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.sheet.DocumentSheetUiState
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.sheet.DocumentSheetViewModel
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.sheet.SheetPage
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DocumentDialogWrapper(
    onHomeAction: (HomeUiAction) -> Unit,
    modifier: Modifier = Modifier,
    sheetViewModel: DocumentSheetViewModel = koinViewModel(),
) {
    val uiState by sheetViewModel.uiState.collectAsStateWithLifecycle()

    if (!uiState.isVisible) return

    val windowSizeClass = LocalWindowWidthState.current
    val isCompact = windowSizeClass == WindowWidthSizeClass.Compact
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Bridge: sheet effects that require HomeViewModel's use-cases
    HandleSheetEffects(
        sheetViewModel = sheetViewModel,
        onHomeAction = onHomeAction,
        uiState = uiState,
    )

    ModalBottomSheet(
        onDismissRequest = { sheetViewModel.onAction(DocumentSheetAction.Dismiss) },
        sheetState = sheetState,
        modifier = modifier,
    ) {
        NavDisplay(
            modifier = Modifier.animateContentSize(
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
            ),
            backStack = if (isCompact) uiState.pageStack else uiState.pageStack.take(1),
            onBack = { sheetViewModel.onAction(DocumentSheetAction.Back) },
            entryProvider = entryProvider {
                entry<SheetPage.Actions> {
                    val doc = uiState.activeDocument ?: return@entry
                    DocumentActionsContent(
                        scannedDocument = doc,
                        onSave = { sheetViewModel.onRequestSave() },
                        onShare = { sheetViewModel.onRequestShare() },
                        onDelete = { sheetViewModel.onAction(DocumentSheetAction.NavigateToDelete) },
                        onModifyFields = { sheetViewModel.onAction(DocumentSheetAction.NavigateToEdit) },
                    )
                }
                entry<SheetPage.Edit> {
                    val doc = uiState.activeDocument ?: return@entry
                    EditDocumentDetailsSheet(
                        doc = doc,
                        state = uiState.editUiState,
                        onTitleChange = { sheetViewModel.onAction(DocumentSheetAction.UpdateTitle(it)) },
                        onDescriptionChange = { sheetViewModel.onAction(DocumentSheetAction.UpdateDescription(it)) },
                        onPopDialog = { sheetViewModel.onAction(DocumentSheetAction.Back) },
                        onConfirmEdit = { sheetViewModel.onAction(DocumentSheetAction.ConfirmEdit) },
                    )
                }
                entry<SheetPage.Delete> {
                    val doc = uiState.activeDocument ?: return@entry
                    DeleteDocumentSheet(
                        document = doc,
                        onDismiss = { sheetViewModel.onAction(DocumentSheetAction.Back) },
                        onConfirm = { sheetViewModel.onAction(DocumentSheetAction.ConfirmDelete) },
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
        when (uiState.activePage) {
            is SheetPage.Edit -> {
                    val doc = uiState.activeDocument ?: return
                    EditDocumentDetailsDialog(
                        doc = doc,
                        state = uiState.editUiState,
                        onTitleChange = { sheetViewModel.onAction(DocumentSheetAction.UpdateTitle(it)) },
                        onDescriptionChange = { sheetViewModel.onAction(DocumentSheetAction.UpdateDescription(it)) },
                        onDismiss = { sheetViewModel.onAction(DocumentSheetAction.Back) },
                        onConfirmEdit = { sheetViewModel.onAction(DocumentSheetAction.ConfirmEdit) },
                        modifier = Modifier.widthIn(max = 560.dp),
                    )
                }
            is SheetPage.Delete -> {
                val doc = uiState.activeDocument ?: return
                DeleteDocumentDialog(
                    scannedDocument = doc,
                    onDismiss = { sheetViewModel.onAction(DocumentSheetAction.Back) },
                    onConfirm = { sheetViewModel.onAction(DocumentSheetAction.ConfirmDelete) },
                    modifier = Modifier.widthIn(max = 560.dp),
                )
            }
            else -> Unit
        }
    }
}

@Composable
private fun HandleSheetEffects(
    sheetViewModel: DocumentSheetViewModel,
    onHomeAction: (HomeUiAction) -> Unit,
    uiState: DocumentSheetUiState,
) {
    // Collect one-shot effects and bridge them to HomeViewModel actions.
    // We use LaunchedEffect at the call-site in HomeScreen instead, so this
    // is intentionally left as a documentation anchor. The actual collection
    // happens in HomeScreen via sheetViewModel.effects.
}
