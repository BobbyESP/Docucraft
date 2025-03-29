package com.bobbyesp.docucraft.feature.pdfscanner.domain

data class FilterOptions(
    val minPageCount: Int? = null,
    val minFileSize: Long? = null,
    val dateRange: Pair<Long, Long>? = null,
    val sortBy: SortOption = SortOption.DateDesc,
)