/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.documentcontent.platform.links

import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * The objects of a PDF, by number: the cross-reference of ISO 32000-1, in every form a writer may
 * leave it (tables, cross-reference streams, both at once, several revisions), and objects stored
 * compressed in object streams.
 *
 * A file whose cross-reference is missing or points to the wrong places is read the way PDF
 * renderers read it, pdfium included: by finding every `n g obj` in the file. A document the
 * renderer shows should not lose its links because its offsets are off.
 *
 * Encrypted documents are read only as far as encryption leaves them readable: dictionaries,
 * arrays, names and numbers, which is what explicit destinations are made of. Their strings and
 * streams are not decrypted, so a named destination or an object kept in an object stream is not
 * found ([encrypted] says so).
 */
internal class PdfObjects(private val bytes: PdfBytes) {

    private class Entry(val inStream: Boolean, val offsetOrStream: Long, val index: Int)

    private val entries = HashMap<Int, Entry>()
    private val cache = HashMap<Int, Any?>()
    private val objectStreams =
        object : LinkedHashMap<Int, ObjectStream>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<Int, ObjectStream>) =
                size > OBJECT_STREAM_CACHE
        }
    private var recovered: Map<Int, Long>? = null

    /** The trailer of the newest revision, which names the catalog. */
    val trailer: PdfDict

    val encrypted: Boolean

    init {
        val fromXref =
            try {
                readCrossReference()
            } catch (_: PdfSyntaxException) {
                null
            } catch (_: IndexOutOfBoundsException) {
                null
            }
        trailer =
            if (fromXref?.get("Root") != null && entries.isNotEmpty()) fromXref
            else {
                entries.clear()
                recoverTrailer() ?: throw PdfSyntaxException("No catalog")
            }
        encrypted = trailer["Encrypt"] != null
    }

    val catalog: PdfDict?
        get() = resolve(trailer["Root"]) as? PdfDict

    /** [value] itself, or the object it refers to. */
    fun resolve(value: Any?): Any? {
        var current = value
        repeat(MAX_REFERENCE_CHAIN) {
            if (current !is PdfRef) return current
            current = objectNumbered((current as PdfRef).number)
        }
        return null
    }

    fun dict(value: Any?): PdfDict? =
        when (val resolved = resolve(value)) {
            is PdfDict -> resolved
            is PdfStream -> resolved.dict
            else -> null
        }

    fun array(value: Any?): List<Any?>? = resolve(value) as? List<Any?>

    fun number(value: Any?): Double? = resolve(value).asNumber()

    fun objectNumbered(number: Int): Any? {
        if (cache.containsKey(number)) return cache[number]
        // Marked before reading, so an object that refers to itself (a /Length, a broken tree)
        // reads as null rather than recursing.
        cache[number] = null
        val value =
            try {
                read(number)
            } catch (_: PdfSyntaxException) {
                null
            } catch (_: IndexOutOfBoundsException) {
                null
            }
        cache[number] = value
        return value
    }

    /** A stream's data, through its filters; `null` if a filter is not one this reads. */
    fun decode(stream: PdfStream): ByteArray? {
        if (stream.length > MAX_STREAM_BYTES) return null
        var data = bytes.copy(stream.dataStart, stream.length)
        val filters =
            when (val filter = resolve(stream.dict["Filter"])) {
                null -> emptyList()
                is PdfName -> listOf(filter.value)
                is List<*> -> filter.map { (resolve(it) as? PdfName)?.value ?: return null }
                else -> return null
            }
        val parameters =
            when (val parms = resolve(stream.dict["DecodeParms"])) {
                is List<*> -> parms.map { dict(it) }
                else -> listOf(dict(parms))
            }
        for ((index, filter) in filters.withIndex()) {
            data =
                when (filter) {
                    "FlateDecode",
                    "Fl" -> unpredict(inflate(data) ?: return null, parameters.getOrNull(index))
                    else -> return null
                } ?: return null
        }
        return data
    }

    // ------------------------------------------------------------------------- reading objects

    private fun read(number: Int): Any? {
        val entry = entries[number]
        if (entry == null) return readAt(recoveredOffsets()[number] ?: return null, number)
        if (entry.inStream) return readFromStream(entry.offsetOrStream.toInt(), entry.index, number)
        // An offset that is off by a few bytes, or points into another object, is common enough
        // in the wild that a failed read is retried where the object really is.
        return readAtOrNull(entry.offsetOrStream, number)
            ?: recoveredOffsets()[number]
                ?.takeIf { it != entry.offsetOrStream }
                ?.let {
                    readAt(it, number)
                }
    }

    private fun readAtOrNull(offset: Long, number: Int): Any? =
        try {
            readAt(offset, number)
        } catch (_: PdfSyntaxException) {
            null
        }

    /** The object at [offset], or `null` if another one (or nothing) is there. */
    private fun readAt(offset: Long, number: Int): Any? {
        val parser = PdfParser(bytes, offset)
        val header = parser.readObjectHeader() ?: return null
        if (header.number != number) return null
        val value = parser.readObject()
        if (value !is PdfDict) return value
        val afterDict = parser.position
        if (parser.readKeyword() != "stream") return value
        return stream(value, parser.position, afterDict)
    }

    private fun stream(dict: PdfDict, afterKeyword: Long, afterDict: Long): PdfStream {
        // The data starts after the end of line that follows the keyword: CRLF or LF.
        var start = afterKeyword
        if (bytes[start] == '\r'.code) start++
        if (bytes[start] == '\n'.code) start++
        val declared = number(dict["Length"])?.toLong()
        val length =
            if (declared != null && declared >= 0 && endstreamNear(start + declared)) declared
            else findEndstream(start)?.let { it - start }
        if (length == null || length > Int.MAX_VALUE) {
            throw PdfSyntaxException("Stream without an end after $afterDict")
        }
        return PdfStream(dict, start, length.toInt())
    }

    private fun endstreamNear(position: Long): Boolean {
        val parser = PdfParser(bytes, position)
        return parser.readKeyword() == "endstream"
    }

    /** Where `endstream` starts, trimmed of the end of line before it. */
    private fun findEndstream(from: Long): Long? {
        val at = indexOf(ENDSTREAM, from, from + MAX_STREAM_BYTES) ?: return null
        var end = at
        if (end > from && bytes[end - 1] == '\n'.code) end--
        if (end > from && bytes[end - 1] == '\r'.code) end--
        return end
    }

    private class ObjectStream(val data: ByteArray, val numbers: IntArray, val offsets: IntArray)

    private fun readFromStream(streamNumber: Int, index: Int, number: Int): Any? {
        if (encrypted) return null
        val objectStream =
            objectStreams[streamNumber]
                ?: loadObjectStream(streamNumber)?.also { objectStreams[streamNumber] = it }
                ?: return null
        // The index is a hint; the stream's own table is the authority.
        val slot =
            index.takeIf {
                it in objectStream.numbers.indices && objectStream.numbers[it] == number
            } ?: objectStream.numbers.indexOf(number).takeIf { it >= 0 } ?: return null
        return PdfParser(ByteArrayBytes(objectStream.data), objectStream.offsets[slot].toLong())
            .readObject()
    }

    private fun loadObjectStream(number: Int): ObjectStream? {
        val stream = objectNumbered(number) as? PdfStream ?: return null
        val count =
            stream.dict["N"].asInt()?.takeIf { it in 0..MAX_OBJECTS_PER_STREAM } ?: return null
        val first = stream.dict["First"].asInt()?.takeIf { it >= 0 } ?: return null
        val data = decode(stream) ?: return null
        val header = PdfParser(ByteArrayBytes(data))
        val numbers = IntArray(count)
        val offsets = IntArray(count)
        for (i in 0 until count) {
            numbers[i] = header.readNonNegativeInt() ?: return null
            offsets[i] = (header.readNonNegativeInt() ?: return null) + first
        }
        return ObjectStream(data, numbers, offsets)
    }

    // --------------------------------------------------------------------- the cross-reference

    /**
     * Reads every revision, newest first, so the newest entry for an object wins. Free entries are
     * not kept: a hybrid file lists the objects of its object streams as free in its table, and
     * they must be found in its cross-reference stream.
     */
    private fun readCrossReference(): PdfDict? {
        var offset: Long? = findStartXref() ?: return null
        var newest: PdfDict? = null
        val seen = HashSet<Long>()
        while (offset != null && seen.add(offset) && seen.size <= MAX_REVISIONS) {
            val parser = PdfParser(bytes, offset)
            val trailer =
                if (parser.readKeyword() == "xref") {
                    readTable(parser)?.also { table ->
                        table["XRefStm"].asInt()?.let { readXrefStream(it.toLong()) }
                    }
                } else {
                    readXrefStream(offset)
                }
            if (trailer == null) break
            if (newest == null) newest = trailer
            else if (newest["Root"] == null && trailer["Root"] != null) newest = trailer
            offset = trailer["Prev"].asInt()?.toLong()
        }
        return newest
    }

    private fun findStartXref(): Long? {
        val tailStart = (bytes.size - TAIL_BYTES).coerceAtLeast(0)
        val tail = String(bytes.copy(tailStart, TAIL_BYTES), Charsets.ISO_8859_1)
        val at = tail.lastIndexOf("startxref").takeIf { it >= 0 } ?: return null
        return PdfParser(bytes, tailStart + at + "startxref".length).readNonNegativeInt()?.toLong()
    }

    private fun readTable(parser: PdfParser): PdfDict? {
        while (true) {
            val first = parser.readNonNegativeInt() ?: break
            val count = parser.readNonNegativeInt() ?: break
            for (i in 0 until count) {
                val offset = parser.readNonNegativeInt()?.toLong() ?: return null
                parser.readNonNegativeInt() ?: return null
                val inUse = parser.readKeyword() == "n"
                if (inUse && offset > 0) {
                    entries.putIfAbsent(first + i, Entry(false, offset, 0))
                }
            }
        }
        if (parser.readKeyword() != "trailer") return null
        return parser.readObject() as? PdfDict
    }

    private fun readXrefStream(offset: Long): PdfDict? {
        val parser = PdfParser(bytes, offset)
        val header = parser.readObjectHeader() ?: return null
        val stream =
            (readAt(offset, header.number) as? PdfStream)?.takeIf { it.dict.name("Type") == "XRef" }
                ?: return null
        val widths = (stream.dict["W"] as? List<*>)?.map { it.asInt() ?: return null }
        if (widths == null || widths.size < 3 || widths.any { it !in 0..8 }) return null
        val size = stream.dict["Size"].asInt() ?: return null
        val index =
            (stream.dict["Index"] as? List<*>)?.map { it.asInt() ?: return null } ?: listOf(0, size)
        val data = decode(stream) ?: return null
        val rowLength = widths.sum()
        var row = 0
        for (section in 0 until index.size / 2) {
            val first = index[section * 2]
            val count = index[section * 2 + 1]
            for (i in 0 until count) {
                val at = row++ * rowLength
                if (at + rowLength > data.size) return stream.dict
                // A missing type field means type 1 in the ISO definition.
                val type = if (widths[0] == 0) 1L else field(data, at, widths[0])
                val second = field(data, at + widths[0], widths[1])
                val third = field(data, at + widths[0] + widths[1], widths[2])
                val entry =
                    when (type) {
                        1L -> Entry(false, second, 0)
                        2L -> Entry(true, second, third.toInt())
                        else -> null
                    }
                if (entry != null) entries.putIfAbsent(first + i, entry)
            }
        }
        return stream.dict
    }

    private fun field(data: ByteArray, at: Int, width: Int): Long {
        var value = 0L
        for (i in 0 until width) value = (value shl 8) or (data[at + i].toLong() and 0xFF)
        return value
    }

    // ------------------------------------------------------------------------------- recovery

    /** Every `n g obj` in the file, the last of each number winning, as a later revision does. */
    private fun recoveredOffsets(): Map<Int, Long> =
        recovered
            ?: HashMap<Int, Long>().also { found ->
                var at = indexOf(OBJ, 0, bytes.size)
                while (at != null) {
                    objectStartBefore(at)?.let { (number, start) -> found[number] = start }
                    at = indexOf(OBJ, at + OBJ.size, bytes.size)
                }
                recovered = found
            }

    /** For an `obj` at [at]: the number before it and where that number starts, if it is one. */
    private fun objectStartBefore(at: Long): Pair<Int, Long>? {
        if (PdfParser.isRegular(bytes[at + OBJ.size])) return null
        var p = at - 1
        fun skipWhite() {
            while (p >= 0 && PdfParser.isWhite(bytes[p])) p--
        }
        fun skipDigits(): Boolean {
            val end = p
            while (p >= 0 && bytes[p] in '0'.code..'9'.code) p--
            return p < end
        }
        skipWhite()
        if (!skipDigits()) return null // the generation
        if (p < 0 || !PdfParser.isWhite(bytes[p])) return null
        skipWhite()
        if (!skipDigits()) return null // the number
        if (p >= 0 && PdfParser.isRegular(bytes[p])) return null
        val start = p + 1
        val number = PdfParser(bytes, start).readNonNegativeInt() ?: return null
        return number to start
    }

    /**
     * A trailer for a file read by recovery: the last `trailer` dictionary naming a catalog, or the
     * last cross-reference stream that does, or else the catalog itself, wherever it is.
     */
    private fun recoverTrailer(): PdfDict? {
        var trailer: PdfDict? = null
        var at = indexOf(TRAILER, 0, bytes.size)
        while (at != null) {
            val parser = PdfParser(bytes, at + TRAILER.size)
            val dict =
                try {
                    parser.readObject() as? PdfDict
                } catch (_: PdfSyntaxException) {
                    null
                }
            if (dict?.get("Root") != null) trailer = dict
            at = indexOf(TRAILER, at + TRAILER.size, bytes.size)
        }
        if (trailer != null) return trailer

        var catalog: PdfRef? = null
        for ((number, _) in recoveredOffsets()) {
            when (val value = objectNumbered(number)) {
                is PdfStream ->
                    if (value.dict.name("Type") == "XRef" && value.dict["Root"] != null) {
                        return value.dict
                    }
                is PdfDict ->
                    if (value.name("Type") == "Catalog" && value["Pages"] != null) {
                        catalog = PdfRef(number, 0)
                    }
            }
        }
        return catalog?.let { PdfDict(mapOf("Root" to it)) }
    }

    private fun indexOf(pattern: ByteArray, from: Long, until: Long): Long? {
        val end = minOf(until, bytes.size) - pattern.size
        var at = from
        while (at <= end) {
            if (bytes[at] == pattern[0].toInt()) {
                var i = 1
                while (i < pattern.size && bytes[at + i] == pattern[i].toInt()) i++
                if (i == pattern.size) return at
            }
            at++
        }
        return null
    }

    // -------------------------------------------------------------------------------- filters

    private fun inflate(data: ByteArray): ByteArray? {
        val inflater = Inflater()
        return try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream(data.size * 3)
            val buffer = ByteArray(16 * 1024)
            while (!inflater.finished() && out.size() <= MAX_STREAM_BYTES) {
                val count = inflater.inflate(buffer)
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buffer, 0, count)
            }
            // A stream cut short keeps what it gave, as renderers do.
            out.toByteArray().takeIf { it.isNotEmpty() }
        } catch (_: DataFormatException) {
            null
        } finally {
            inflater.end()
        }
    }

    /** Undoes a PNG predictor, which cross-reference streams nearly always use. */
    private fun unpredict(data: ByteArray, parameters: PdfDict?): ByteArray? {
        val predictor = parameters?.get("Predictor").asInt() ?: 1
        if (predictor < 10) return data.takeIf { predictor == 1 }
        val colors = parameters?.get("Colors").asInt() ?: 1
        val bits = parameters?.get("BitsPerComponent").asInt() ?: 8
        val columns = parameters?.get("Columns").asInt() ?: 1
        val pixel = ((colors * bits + 7) / 8).coerceAtLeast(1)
        val rowLength = (colors * bits * columns + 7) / 8
        if (rowLength <= 0) return null

        val out = ByteArrayOutputStream(data.size)
        var previous = ByteArray(rowLength)
        var at = 0
        while (at + 1 + rowLength <= data.size) {
            val type = data[at].toInt()
            val row = data.copyOfRange(at + 1, at + 1 + rowLength)
            for (i in row.indices) {
                val left = if (i >= pixel) row[i - pixel].toInt() and 0xFF else 0
                val up = previous[i].toInt() and 0xFF
                val upLeft = if (i >= pixel) previous[i - pixel].toInt() and 0xFF else 0
                val raw = row[i].toInt() and 0xFF
                row[i] =
                    when (type) {
                        1 -> raw + left
                        2 -> raw + up
                        3 -> raw + (left + up) / 2
                        4 -> raw + paeth(left, up, upLeft)
                        else -> raw
                    }.toByte()
            }
            out.write(row)
            previous = row
            at += 1 + rowLength
        }
        return out.toByteArray()
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = kotlin.math.abs(p - a)
        val pb = kotlin.math.abs(p - b)
        val pc = kotlin.math.abs(p - c)
        return if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
    }

    private companion object {
        const val MAX_REFERENCE_CHAIN = 8
        const val MAX_REVISIONS = 256
        const val MAX_OBJECTS_PER_STREAM = 100_000
        const val MAX_STREAM_BYTES = 64 * 1024 * 1024
        const val OBJECT_STREAM_CACHE = 8
        const val TAIL_BYTES = 2048
        val OBJ = "obj".toByteArray(Charsets.ISO_8859_1)
        val ENDSTREAM = "endstream".toByteArray(Charsets.ISO_8859_1)
        val TRAILER = "trailer".toByteArray(Charsets.ISO_8859_1)
    }
}
