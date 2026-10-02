/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db.entity

/**
 * Who keeps a document's file. It is what tells the two kinds of row in `documents` apart, and it
 * changes once at most: saving a linked document into the library makes it managed.
 */
enum class DocumentCustody {
    /** The file is in the app's own storage, and the app answers for it. */
    MANAGED,

    /** The file belongs to another app; only a reference to it is kept. */
    LINKED,
}
