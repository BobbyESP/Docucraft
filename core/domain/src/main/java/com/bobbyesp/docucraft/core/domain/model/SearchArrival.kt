/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.domain.model

/** What the viewer does with the query a document was found by, when opened from the search. */
enum class SearchArrival {
    /** Lights the matches and scrolls to the first, until the reader touches the document. */
    HIGHLIGHT,

    /** Opens the viewer's find bar on the query, with its matches and arrows. */
    OPEN_FIND_BAR,

    /** Opens the document plainly. */
    NOTHING,
}
