/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.documentcontent.platform.links

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

/**
 * Writes small PDFs for the reader's tests, in the two shapes writers produce: a classic one, with
 * a cross-reference table, and a compressed one, with its dictionaries in an object stream and a
 * cross-reference stream behind a PNG predictor, as pdfTeX and most modern writers leave them.
 *
 * Objects are given as their PDF source, without `n 0 obj`; streams are not supported, since the
 * reader only needs dictionaries and arrays.
 */
internal class TestPdf {
    private val bodies = ArrayList<String?>()

    /** How many objects there are; the last one's number. */
    val size: Int
        get() = bodies.size

    fun reserve(): Int {
        bodies += null
        return bodies.size
    }

    fun put(number: Int, body: String): Int {
        bodies[number - 1] = body
        return number
    }

    fun add(body: String): Int = put(reserve(), body)

    /**
     * @param shiftOffsets Moves every offset in the table by this much, as a file edited by hand or
     *   truncated and repaired would have them.
     * @param trailerExtra More of the trailer, such as an `/Encrypt`.
     */
    fun classic(root: Int, shiftOffsets: Int = 0, trailerExtra: String = ""): ByteArray {
        val out = Output()
        out.write("%PDF-1.4\n")
        val offsets =
            bodies.indices.map { index ->
                out.size.also { out.write("${index + 1} 0 obj\n${bodies[index]}\nendobj\n") }
            }
        val xref = out.size
        out.write("xref\n0 ${bodies.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { out.write("%010d 00000 n \n".format(it + shiftOffsets)) }
        out.write("trailer\n<< /Size ${bodies.size + 1} /Root $root 0 R $trailerExtra >>\n")
        out.write("startxref\n$xref\n%%EOF\n")
        return out.bytes()
    }

    /** [compressed] objects go into one object stream; the rest are written as usual. */
    fun compressed(root: Int, compressed: Set<Int>): ByteArray {
        val out = Output()
        out.write("%PDF-1.5\n")
        val direct = HashMap<Int, Int>()
        for (number in 1..bodies.size) {
            if (number in compressed) continue
            direct[number] = out.size
            out.write("$number 0 obj\n${bodies[number - 1]}\nendobj\n")
        }

        val streamNumber = bodies.size + 1
        val members = compressed.sorted()
        val content = StringBuilder()
        val header = StringBuilder()
        for (number in members) {
            header.append("$number ${content.length} ")
            content.append(bodies[number - 1]).append('\n')
        }
        val first = header.length
        val packed = deflate((header.toString() + content).toByteArray(Charsets.ISO_8859_1))
        direct[streamNumber] = out.size
        out.write(
            "$streamNumber 0 obj\n<< /Type /ObjStm /N ${members.size} /First $first " +
                "/Filter /FlateDecode /Length ${packed.size} >>\nstream\n"
        )
        out.write(packed)
        out.write("\nendstream\nendobj\n")

        val xrefNumber = streamNumber + 1
        val xrefOffset = out.size
        direct[xrefNumber] = xrefOffset
        val size = xrefNumber + 1
        val rows = ByteArrayOutputStream()
        var previous = ByteArray(ROW)
        for (number in 0 until size) {
            val row = ByteArray(ROW)
            when {
                number in direct -> row.set(type = 1, second = direct.getValue(number), third = 0)
                number in compressed ->
                    row.set(type = 2, second = streamNumber, third = members.indexOf(number))
                else -> row.set(type = 0, second = 0, third = 0)
            }
            // PNG "Up": each byte minus the one above it.
            rows.write(2)
            for (i in 0 until ROW) rows.write((row[i] - previous[i]) and 0xFF)
            previous = row
        }
        val xrefData = deflate(rows.toByteArray())
        out.write(
            "$xrefNumber 0 obj\n<< /Type /XRef /Size $size /W [1 4 2] /Root $root 0 R " +
                "/Filter /FlateDecode /DecodeParms << /Predictor 12 /Columns $ROW >> " +
                "/Length ${xrefData.size} >>\nstream\n"
        )
        out.write(xrefData)
        out.write("\nendstream\nendobj\nstartxref\n$xrefOffset\n%%EOF\n")
        return out.bytes()
    }

    private fun ByteArray.set(type: Int, second: Int, third: Int) {
        this[0] = type.toByte()
        for (i in 0 until 4) this[1 + i] = (second shr (8 * (3 - i))).toByte()
        for (i in 0 until 2) this[5 + i] = (third shr (8 * (1 - i))).toByte()
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return out.toByteArray()
    }

    private class Output {
        private val stream = ByteArrayOutputStream()

        val size: Int
            get() = stream.size()

        fun write(text: String) = stream.write(text.toByteArray(Charsets.ISO_8859_1))

        fun write(bytes: ByteArray) = stream.write(bytes)

        fun bytes(): ByteArray = stream.toByteArray()
    }

    companion object {
        private const val ROW = 7

        /**
         * Appends a revision to [base]: the objects in [changed], replaced, and a table pointing
         * back to the previous one, as an editor saving incrementally does.
         */
        fun update(base: ByteArray, root: Int, size: Int, changed: Map<Int, String>): ByteArray {
            val text = String(base, Charsets.ISO_8859_1)
            val previous =
                text.substringAfterLast("startxref").trim().substringBefore('\n').trim().toInt()
            val out = ByteArrayOutputStream()
            out.write(base)
            val offsets = changed.mapValues { (number, body) ->
                out.size().also {
                    out.write("$number 0 obj\n$body\nendobj\n".toByteArray(Charsets.ISO_8859_1))
                }
            }
            val xref = out.size()
            val table = StringBuilder("xref\n")
            for ((number, offset) in offsets.toSortedMap()) {
                table.append("$number 1\n").append("%010d 00000 n \n".format(offset))
            }
            table.append("trailer\n<< /Size $size /Root $root 0 R /Prev $previous >>\n")
            table.append("startxref\n$xref\n%%EOF\n")
            out.write(table.toString().toByteArray(Charsets.ISO_8859_1))
            return out.toByteArray()
        }
    }
}
