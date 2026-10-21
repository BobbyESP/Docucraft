/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain

/**
 * A closed interval of time, in epoch milliseconds.
 *
 * A named type rather than a `Pair`, so that neither side has to remember which half is which, and
 * so Compose can infer that it is stable.
 */
data class DateRange(val fromEpochMillis: Long, val toEpochMillis: Long) {
    operator fun contains(epochMillis: Long): Boolean =
        epochMillis in fromEpochMillis..toEpochMillis
}

/** Which documents the list shows, and in what order. */
/**
 * @property favoritesOnly Only the documents marked as favorites.
 * @property tagUuids Only the documents that carry every one of these tags. Empty for no filter.
 */
data class FilterOptions(
    val minPageCount: Int?,
    val minFileSize: Long?,
    val dateRange: DateRange?,
    val sortBy: SortOption,
    val favoritesOnly: Boolean = false,
    val tagUuids: Set<String> = emptySet(),
) {
    /** Whether the user narrowed the library down by how it is organized. */
    val narrowsByOrganization: Boolean
        get() = favoritesOnly || tagUuids.isNotEmpty()

    companion object {
        val default =
            FilterOptions(
                minPageCount = null,
                minFileSize = null,
                dateRange = null,
                sortBy = SortOption.DateDesc,
            )
    }
}
