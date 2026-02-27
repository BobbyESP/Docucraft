package com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.sheet

/**
 * Represents the distinct navigation destinations within the document bottom sheet.
 *
 * Using a sealed interface for navigation pages ensures stable backstack entries,
 * decoupling the navigation state from the specific document data being modified.
 */
sealed interface SheetPage {
    data object Actions : SheetPage
    data object Edit : SheetPage
    data object Delete : SheetPage
}


