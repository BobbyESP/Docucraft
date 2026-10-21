/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner

import com.bobbyesp.docucraft.feature.docscanner.domain.search.SearchHit
import com.bobbyesp.docucraft.feature.docscanner.domain.search.SearchIndex

/**
 * An index that answers what it is told to, and remembers what it was asked.
 *
 * @property answer What a search for a query finds. Nothing, until a test says otherwise.
 */
class FakeSearchIndex(var answer: (query: String) -> List<SearchHit> = { emptyList() }) :
    SearchIndex {

    val queries = mutableListOf<String>()

    /** The same hits whatever is searched for. */
    var hits: List<SearchHit>
        get() = answer("")
        set(value) {
            answer = { value }
        }

    override suspend fun search(query: String): List<SearchHit> {
        queries += query
        return answer(query)
    }
}
