/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.data.content

/**
 * A word of a page's text and where it is in that text: the character indices the platform's
 * `selectContent` takes to measure it.
 */
internal data class WordSpan(val text: String, val start: Int, val endExclusive: Int)

/**
 * Splits a page's text, as `PdfRenderer.Page.getTextContents()` gives it (one block, lines
 * separated by `\r\n`), into lines of words, keeping each word's position in the text. Blank lines
 * stay, since they separate paragraphs; trailing ones go.
 */
internal fun splitPageText(text: String): List<List<WordSpan>> {
    val lines = mutableListOf<List<WordSpan>>()
    var lineStart = 0
    for (lineBreak in LineBreak.findAll(text) + sequenceOf(null)) {
        val lineEnd = lineBreak?.range?.first ?: text.length
        lines +=
            Word.findAll(text.substring(lineStart, lineEnd))
                .map {
                    WordSpan(
                        text = it.value,
                        start = lineStart + it.range.first,
                        endExclusive = lineStart + it.range.last + 1,
                    )
                }
                .toList()
        lineStart = lineBreak?.range?.last?.plus(1) ?: text.length
    }
    return lines.dropLastWhile { it.isEmpty() }
}

private val LineBreak = Regex("\r\n|\n|\r")
private val Word = Regex("\\S+")
