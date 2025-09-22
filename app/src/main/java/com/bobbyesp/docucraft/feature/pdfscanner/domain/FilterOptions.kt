package com.bobbyesp.docucraft.feature.pdfscanner.domain

data class FilterOptions(
    val minPageCount: Int?,
    val minFileSize: Long?,
    val dateRange: Pair<Long, Long>?,
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
