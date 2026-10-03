/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db

import com.bobbyesp.documentcontent.NormalizedRect
import com.bobbyesp.documentcontent.TextLine
import com.bobbyesp.documentcontent.TextWord
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * How the words of a recognized page, and where each one is, are kept in `page_layouts.data`: the
 * lines in reading order, each with its words, each word with its text and its box as four
 * fractions of the page.
 *
 * A format of its own rather than JSON: a page is hundreds of words, a library is thousands of
 * pages, and all of it is only ever read whole, to select text on the page it belongs to.
 *
 * [VERSION] is stored beside the data. A layout written in a format this cannot read is treated as
 * not there, and the page is recognized again when it is next needed.
 */
internal object PageLayoutCodec {
    const val VERSION = 1

    fun encode(lines: List<TextLine>): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(lines.size)
            for (line in lines) {
                out.writeInt(line.words.size)
                for (word in line.words) {
                    out.writeUTF(word.text)
                    out.writeFloat(word.bounds.left)
                    out.writeFloat(word.bounds.top)
                    out.writeFloat(word.bounds.right)
                    out.writeFloat(word.bounds.bottom)
                }
            }
        }
        return bytes.toByteArray()
    }

    /** @return The lines, or `null` when [formatVersion] is not one this reads or [data] is cut. */
    fun decode(formatVersion: Int, data: ByteArray): List<TextLine>? {
        if (formatVersion != VERSION) return null
        return try {
            DataInputStream(ByteArrayInputStream(data)).use { input ->
                List(input.readInt()) {
                    TextLine(
                        List(input.readInt()) {
                            TextWord(
                                text = input.readUTF(),
                                bounds =
                                    NormalizedRect(
                                        left = input.readFloat(),
                                        top = input.readFloat(),
                                        right = input.readFloat(),
                                        bottom = input.readFloat(),
                                    ),
                            )
                        }
                    )
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
