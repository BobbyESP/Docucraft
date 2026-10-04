/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.FakeSearchIndex
import com.bobbyesp.docucraft.feature.docscanner.domain.search.SearchHit
import com.bobbyesp.docucraft.feature.docscanner.domain.search.SearchPassage
import com.bobbyesp.docucraft.feature.docscanner.domain.search.TextRange
import com.bobbyesp.docucraft.feature.docscanner.testDocument
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchDocumentsUseCaseTest {

    private val index = FakeSearchIndex()
    private val search = SearchDocumentsUseCase(index)

    private val invoice = testDocument(uuid = "invoice", title = "Invoice")
    private val contract = testDocument(uuid = "contract", title = "Contract")
    private val notes = testDocument(uuid = "notes", title = "Notes")

    @Test
    fun `results are the library's documents in the order the index ranked them`() = runTest {
        index.hits =
            listOf(
                SearchHit("contract", score = 9.0, passage = null),
                SearchHit("invoice", score = 4.0, passage = null),
            )

        val results = search(listOf(invoice, contract, notes), "anything")

        assertEquals(listOf(contract, invoice), results.map { it.document })
    }

    @Test
    fun `a result carries where in the document's text the match is`() = runTest {
        val passage =
            SearchPassage(pageIndex = 3, text = "the invoice total", listOf(TextRange(4, 11)))
        index.hits = listOf(SearchHit("invoice", score = 1.0, passage = passage))

        val results = search(listOf(invoice), "invoice")

        assertEquals(listOf(SearchResult(invoice, passage)), results)
    }

    // The index and the list are read one after the other. A document deleted in between is in
    // the first and not in the second, and has nothing to be shown as.
    @Test
    fun `a hit for a document the library no longer has is left out`() = runTest {
        index.hits =
            listOf(
                SearchHit("deleted-meanwhile", score = 9.0, passage = null),
                SearchHit("invoice", score = 4.0, passage = null),
            )

        val results = search(listOf(invoice, contract), "anything")

        assertEquals(listOf(invoice), results.map { it.document })
    }

    @Test
    fun `a blank query finds nothing and does not ask the index`() = runTest {
        index.hits = listOf(SearchHit("invoice", score = 1.0, passage = null))

        assertEquals(emptyList<SearchResult>(), search(listOf(invoice), ""))
        assertEquals(emptyList<SearchResult>(), search(listOf(invoice), "   "))
        assertEquals(emptyList<String>(), index.queries)
    }

    @Test
    fun `the query reaches the index as it was typed`() = runTest {
        search(listOf(invoice), "Factura \"luz\"")

        assertEquals(listOf("Factura \"luz\""), index.queries)
    }
}
