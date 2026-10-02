/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.search

import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document

/** Strategy for performing a query-based search, typically against a database or remote API. */
interface QuerySearchStrategy {
    suspend fun search(query: String): List<Document>
}
