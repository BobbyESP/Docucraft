/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.contract

import com.bobbyesp.docucraft.feature.docscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Folder
import com.bobbyesp.docucraft.feature.docscanner.domain.model.RecentDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Tag
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.TagSection

sealed interface HomeStatus {
    data object Idle : HomeStatus

    data object Loading : HomeStatus

    data class Error(val message: String) : HomeStatus
}

/**
 * @property recentDocuments what the Recents carousel shows: the documents used last, the app's own
 *   and other apps'. Empty when there are too few documents for it to add anything the list below
 *   does not already show.
 * @property pinnedFolders the folders pinned to Home, in the order they were pinned.
 * @property tagSections the tags the user gave a section of their own, with their documents.
 * @property notFoundUuids the documents whose file is not there, which are shown saying so.
 * @property scanToReview the uuid of a scan that was just saved and is still to be shown to the
 *   user for review, or `null`. State rather than an effect: the scan is saved while the scanner
 *   still covers the app, when nobody is there to take an effect, and after a process death.
 * @property tags every tag, for the list of documents to be narrowed down by.
 */
data class HomeUiState(
    val status: HomeStatus = HomeStatus.Loading,
    val visibleDocuments: List<Document.Managed> = emptyList(),
    val recentDocuments: List<RecentDocument> = emptyList(),
    val pinnedFolders: List<Folder> = emptyList(),
    val tagSections: List<TagSection> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val notFoundUuids: Set<String> = emptySet(),
    val hasDocuments: Boolean = false,
    val filterOptions: FilterOptions = FilterOptions.default,
    val isScanning: Boolean = false,
    val scanToReview: String? = null,
) {
    val errorMessage: String? = (status as? HomeStatus.Error)?.message

    val hasActiveFilters: Boolean = filterOptions.run {
        minPageCount != null ||
            minFileSize != null ||
            dateRange != null ||
            sortBy != SortOption.DateDesc
    }

    val isEmptyResult: Boolean = visibleDocuments.isEmpty() && hasDocuments
}
