/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.store

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.text.Normalizer

/**
 * Writes the PDFs of the sample library: A4 pages of real text, which the viewer can select and the
 * catalogue can search, with rules, filled boxes and links.
 *
 * Written by hand rather than with the platform's `PdfDocument`, which cannot give a page a link,
 * and the link's preview is one of the screens to show. Text is Helvetica in the encoding of
 * Western European languages, which covers every language the store's graphics are drawn in.
 */
internal class SamplePdf {

    private val pages = mutableListOf<Page>()

    fun page(block: Page.() -> Unit) {
        pages += Page().apply(block)
    }

    val pageCount: Int
        get() = pages.size

    fun bytes(): ByteArray {
        val out = ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()
        fun write(text: String) = out.write(text.toByteArray(Charsets.ISO_8859_1))
        fun obj(body: String) {
            offsets += out.size()
            write("${offsets.size} 0 obj\n$body\nendobj\n")
        }

        // Objects 1 to 4 are the catalogue, the page tree and the two fonts. Each page then takes
        // one for itself, one for its content and one for each of its links.
        var next = FIRST_PAGE_OBJECT
        val pageObjects = pages.map { page -> next.also { next += 2 + page.links.size } }

        write("%PDF-1.4\n")
        obj("<< /Type /Catalog /Pages 2 0 R >>")
        obj(
            "<< /Type /Pages /Count ${pages.size} " +
                "/Kids [${pageObjects.joinToString(" ") { "$it 0 R" }}] >>"
        )
        obj("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
        obj(
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold " +
                "/Encoding /WinAnsiEncoding >>"
        )
        pages.forEachIndexed { index, page ->
            val number = pageObjects[index]
            val annotations = page.links.indices.joinToString(" ") { "${number + 2 + it} 0 R" }
            obj(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $WIDTH $HEIGHT] " +
                    "/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> " +
                    "/Contents ${number + 1} 0 R /Annots [$annotations] >>"
            )
            val content = page.content.toString()
            obj("<< /Length ${content.length} >>\nstream\n${content}endstream")
            for (link in page.links) {
                obj(
                    "<< /Type /Annot /Subtype /Link /Border [0 0 0] " +
                        "/Rect [${link.left} ${link.bottom} ${link.right} ${link.top}] " +
                        "/A << /S /URI /URI (${link.url}) >> >>"
                )
            }
        }

        val xref = out.size()
        write("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        for (offset in offsets) write("%010d 00000 n \n".format(offset))
        write("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.toByteArray()
    }

    /**
     * One page. Positions are in points from its top left corner, as a page is read, and a text's
     * `y` is its baseline.
     */
    class Page {
        internal val content = StringBuilder()
        internal val links = mutableListOf<Link>()

        fun text(
            x: Float,
            y: Float,
            text: String,
            size: Float,
            bold: Boolean = false,
            gray: Float = INK,
        ) {
            val hex = text.toByteArray(WesternEuropean).joinToString("") { "%02X".format(it) }
            content.append("BT /${if (bold) "F2" else "F1"} $size Tf $gray g ")
            content.append("1 0 0 1 $x ${HEIGHT - y} Tm <$hex> Tj ET\n")
        }

        /**
         * [text] broken into lines no wider than [width], the first on the baseline [y].
         *
         * @return The baseline the line after the last would have.
         */
        fun paragraph(
            x: Float,
            y: Float,
            width: Float,
            text: String,
            size: Float,
            leading: Float = size * 1.5f,
            gray: Float = INK,
        ): Float {
            var baseline = y
            var line = ""
            for (word in text.split(' ')) {
                val longer = if (line.isEmpty()) word else "$line $word"
                if (line.isNotEmpty() && widthOf(longer, size) > width) {
                    text(x, baseline, line, size, gray = gray)
                    baseline += leading
                    line = word
                } else {
                    line = longer
                }
            }
            if (line.isNotEmpty()) {
                text(x, baseline, line, size, gray = gray)
                baseline += leading
            }
            return baseline
        }

        fun rule(x: Float, y: Float, width: Float, gray: Float = RULE, thickness: Float = 0.75f) {
            content.append(
                "$gray G $thickness w $x ${HEIGHT - y} m ${x + width} ${HEIGHT - y} l S\n"
            )
        }

        fun box(x: Float, y: Float, width: Float, height: Float, gray: Float) {
            content.append("$gray g $x ${HEIGHT - y - height} $width $height re f\n")
        }

        /** [label] as a link to [url]: underlined, and tappable over the whole of it. */
        fun link(x: Float, y: Float, label: String, url: String, size: Float) {
            val width = widthOf(label, size)
            text(x, y, label, size, bold = true)
            rule(x, y + 2.5f, width, gray = INK, thickness = 0.8f)
            links +=
                Link(
                    left = x,
                    bottom = HEIGHT - y - size * 0.35f,
                    right = x + width * BOLD_WIDENING,
                    top = HEIGHT - y + size,
                    url = url,
                )
        }
    }

    internal class Link(
        val left: Float,
        val bottom: Float,
        val right: Float,
        val top: Float,
        val url: String,
    )

    companion object {
        const val WIDTH = 595f
        const val HEIGHT = 842f

        /** The grays of the pages, from 0 for black to 1 for white. */
        const val INK = 0.16f
        const val MUTED = 0.45f
        const val RULE = 0.8f
        const val TINT = 0.93f

        private const val FIRST_PAGE_OBJECT = 5

        /** How much wider Helvetica Bold sets a line than Helvetica, near enough for a link. */
        private const val BOLD_WIDENING = 1.07f

        private val WesternEuropean: Charset = Charset.forName("windows-1252")
        private val Marks = Regex("\\p{Mn}+")

        /** The width of [text] set in Helvetica at [size]. */
        fun widthOf(text: String, size: Float): Float = text.sumOf { widthOf(it) } * size / 1000f

        // An accented letter is as wide as the letter under the accent, and what is in neither
        // table is taken for a lower case letter of the commonest width.
        private fun widthOf(char: Char): Int {
            HELVETICA.getOrNull(char.code - 32)?.let {
                return it
            }
            val base = Normalizer.normalize(char.toString(), Normalizer.Form.NFD).replace(Marks, "")
            return base.singleOrNull()?.let { HELVETICA.getOrNull(it.code - 32) } ?: 556
        }

        /** Helvetica's widths for the characters from the space to the tilde, in 1/1000 em. */
        private val HELVETICA =
            intArrayOf(
                    278,
                    278,
                    355,
                    556,
                    556,
                    889,
                    667,
                    191,
                    333,
                    333,
                    389,
                    584,
                    278,
                    333,
                    278,
                    278,
                    556,
                    556,
                    556,
                    556,
                    556,
                    556,
                    556,
                    556,
                    556,
                    556,
                    278,
                    278,
                    584,
                    584,
                    584,
                    556,
                    1015,
                    667,
                    667,
                    722,
                    722,
                    667,
                    611,
                    778,
                    722,
                    278,
                    500,
                    667,
                    556,
                    833,
                    722,
                    778,
                    667,
                    778,
                    722,
                    667,
                    611,
                    722,
                    667,
                    944,
                    667,
                    667,
                    611,
                    278,
                    278,
                    278,
                    469,
                    556,
                    333,
                    556,
                    556,
                    500,
                    556,
                    556,
                    278,
                    556,
                    556,
                    222,
                    222,
                    500,
                    222,
                    833,
                    556,
                    556,
                    556,
                    556,
                    333,
                    500,
                    278,
                    556,
                    500,
                    722,
                    500,
                    500,
                    500,
                    334,
                    260,
                    334,
                    584,
                )
                .toList()
    }
}
