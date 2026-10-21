/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.domain.DateRange
import com.bobbyesp.docucraft.feature.docscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.docscanner.testDocument
import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessDocumentsUseCaseTest {

    private val process = ProcessDocumentsUseCase()

    private val small =
        testDocument(
            uuid = "small",
            title = "Beta",
            createdAtEpochMillis = 300,
            sizeBytes = 100,
            pageCount = 1,
        )
    private val big =
        testDocument(
            uuid = "big",
            title = "Alpha",
            createdAtEpochMillis = 100,
            sizeBytes = 900,
            pageCount = 9,
        )
    private val middle =
        testDocument(
            uuid = "middle",
            title = "Gamma",
            createdAtEpochMillis = 200,
            sizeBytes = 500,
            pageCount = 5,
        )
    private val all = listOf(small, big, middle)

    private fun uuids(filter: FilterOptions = FilterOptions.default, sort: SortOption) =
        process(all, filter, sort).map { it.uuid }

    @Test
    fun `sorts by when a document entered the catalogue`() {
        assertEquals(listOf("small", "middle", "big"), uuids(sort = SortOption.DateDesc))
        assertEquals(listOf("big", "middle", "small"), uuids(sort = SortOption.DateAsc))
    }

    @Test
    fun `sorts by the name shown on screen`() {
        assertEquals(listOf("big", "small", "middle"), uuids(sort = SortOption.NameAsc))
        assertEquals(listOf("middle", "small", "big"), uuids(sort = SortOption.NameDesc))
    }

    // A document is listed by what it is called, whichever of its names that is.
    @Test
    fun `a document without a title sorts by its suggested title, then its original name`() {
        val suggested =
            testDocument(uuid = "suggested", originalName = "Zzz", suggestedTitle = "Aaa")
        val original = testDocument(uuid = "original", originalName = "Mmm")
        val titled = testDocument(uuid = "titled", originalName = "Aaa", title = "Zzz")

        val sorted =
            process(listOf(titled, original, suggested), FilterOptions.default, SortOption.NameAsc)

        assertEquals(listOf("suggested", "original", "titled"), sorted.map { it.uuid })
    }

    @Test
    fun `sorts by size`() {
        assertEquals(listOf("big", "middle", "small"), uuids(sort = SortOption.SizeDesc))
        assertEquals(listOf("small", "middle", "big"), uuids(sort = SortOption.SizeAsc))
    }

    @Test
    fun `keeps the documents with at least the pages and the size asked for`() {
        val filter = FilterOptions.default.copy(minPageCount = 5, minFileSize = 500)

        assertEquals(listOf("middle", "big"), uuids(filter, SortOption.DateDesc))
    }

    @Test
    fun `keeps the documents that entered the catalogue in the range, ends included`() {
        val filter = FilterOptions.default.copy(dateRange = DateRange(200, 300))

        assertEquals(listOf("small", "middle"), uuids(filter, SortOption.DateDesc))
    }

    // Not known is not zero, but it is not "at least five" either.
    @Test
    fun `a document whose pages or size are not known does not pass a minimum`() {
        val unknown = testDocument(uuid = "unknown", sizeBytes = null, pageCount = null)

        assertEquals(
            emptyList<String>(),
            process(
                    listOf(unknown),
                    FilterOptions.default.copy(minPageCount = 1),
                    SortOption.DateDesc,
                )
                .map { it.uuid },
        )
        assertEquals(
            emptyList<String>(),
            process(
                    listOf(unknown),
                    FilterOptions.default.copy(minFileSize = 1),
                    SortOption.DateDesc,
                )
                .map { it.uuid },
        )
        assertEquals(
            listOf("unknown"),
            process(listOf(unknown), FilterOptions.default, SortOption.SizeAsc).map { it.uuid },
        )
    }
}
