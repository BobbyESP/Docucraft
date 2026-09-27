/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer

import com.bobbyesp.documentcontent.ContentOrigin
import com.bobbyesp.documentcontent.DocumentSource
import com.bobbyesp.documentcontent.NormalizedRect
import com.bobbyesp.documentcontent.PageContentProvider
import com.bobbyesp.documentcontent.PageContentResult
import com.bobbyesp.documentcontent.PageContentSession
import com.bobbyesp.documentcontent.PageText
import com.bobbyesp.documentcontent.TextLine
import com.bobbyesp.documentcontent.TextWord

/**
 * Page content served from a map, keeping a record of the sessions opened and the pages read. A
 * page missing from the map fails, as a page outside the document does.
 */
class FakePageContentProvider(
    private val pages: Map<Int, PageContentResult>,
    override val origin: ContentOrigin = ContentOrigin.EMBEDDED,
) : PageContentProvider {

    val sessions = mutableListOf<Session>()

    val opened: Int
        get() = sessions.size

    /** Pages read, across every session. */
    val reads: Int
        get() = sessions.sumOf { it.reads }

    override suspend fun open(document: DocumentSource): PageContentSession =
        Session().also { sessions += it }

    inner class Session : PageContentSession {
        var reads = 0
            private set

        var closed = false
            private set

        override suspend fun page(index: Int): PageContentResult {
            reads++
            return pages[index] ?: PageContentResult.Failed(IndexOutOfBoundsException("$index"))
        }

        override fun close() {
            closed = true
        }
    }
}

/** A page of one line holding [words], left to right from x 0, a word every 0.1. */
fun textPage(vararg words: String, origin: ContentOrigin = ContentOrigin.EMBEDDED) =
    PageContentResult.Available(
        text =
            PageText(
                lines =
                    listOf(
                        TextLine(
                            words.mapIndexed { i, word ->
                                TextWord(
                                    word,
                                    NormalizedRect(0.1f * i, 0.1f, 0.1f * i + 0.08f, 0.14f),
                                )
                            }
                        )
                    ),
                origin = origin,
            ),
        links = emptyList(),
    )
