package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.sheet

sealed interface SheetAction {
    data object Dismiss: SheetAction
    data object Back: SheetAction

    data class Navigate(val page: SheetPage): SheetAction

    data class UpdateTitle(val value: String): SheetAction
    data class UpdateDescription(val value: String): SheetAction
    data object ConfirmEdit: SheetAction

    data object ConfirmDelete: SheetAction

    data object RequestShare: SheetAction
    data object RequestSave: SheetAction
}