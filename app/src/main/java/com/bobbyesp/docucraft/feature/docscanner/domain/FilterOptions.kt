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
data class FilterOptions(
    val minPageCount: Int?,
    val minFileSize: Long?,
    val dateRange: DateRange?,
    val sortBy: SortOption,
) {
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
