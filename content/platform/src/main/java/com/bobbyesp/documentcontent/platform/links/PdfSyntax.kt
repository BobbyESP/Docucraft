/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.documentcontent.platform.links

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * A PDF's bytes, read at any position. A file is read through a small block cache: the parser goes
 * a byte at a time, and the objects it needs (pages, annotations) are scattered over the file.
 */
internal interface PdfBytes {
    val size: Long

    /** The byte at [position], 0 to 255, or -1 outside the file. */
    operator fun get(position: Long): Int

    /** Up to [length] bytes from [position]; fewer at the end of the file. */
    fun copy(position: Long, length: Int): ByteArray
}

internal class ByteArrayBytes(private val array: ByteArray) : PdfBytes {
    override val size: Long = array.size.toLong()

    override fun get(position: Long): Int =
        if (position in 0 until size) array[position.toInt()].toInt() and 0xFF else -1

    override fun copy(position: Long, length: Int): ByteArray {
        if (position !in 0 until size) return ByteArray(0)
        val end = minOf(size, position + length).toInt()
        return array.copyOfRange(position.toInt(), end)
    }
}

internal class ChannelBytes(private val channel: FileChannel) : PdfBytes {
    override val size: Long = channel.size()

    private val block = ByteBuffer.allocate(BLOCK_SIZE)
    private var blockStart = -1L
    private var blockLength = 0

    override fun get(position: Long): Int {
        if (position !in 0 until size) return -1
        if (position < blockStart || position >= blockStart + blockLength) load(position)
        return block.get((position - blockStart).toInt()).toInt() and 0xFF
    }

    override fun copy(position: Long, length: Int): ByteArray {
        if (position !in 0 until size) return ByteArray(0)
        val buffer = ByteBuffer.allocate(minOf(size - position, length.toLong()).toInt())
        var at = position
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer, at)
            if (read <= 0) break
            at += read
        }
        return buffer.array().copyOf(buffer.position())
    }

    private fun load(position: Long) {
        // Aligned, so reading backwards (the trailer) or forwards both stay inside a block.
        val start = position - position % BLOCK_SIZE
        block.clear()
        var at = start
        while (block.hasRemaining()) {
            val read = channel.read(block, at)
            if (read <= 0) break
            at += read
        }
        blockStart = start
        blockLength = block.position()
    }

    private companion object {
        const val BLOCK_SIZE = 8 * 1024
    }
}

// ----------------------------------------------------------------------------------------- objects
//
// A PDF object is a Kotlin value: `null`, Boolean, Long (integers), Double (reals), List<Any?>
// (arrays), or one of the classes below.

internal data class PdfName(val value: String)

/**
 * A string's bytes, undecoded. [key] reads them one char per byte, which keeps them comparable with
 * names, and orders them as the name trees of the format do: byte by byte.
 */
internal class PdfString(val bytes: ByteArray) {
    val key: String
        get() = String(bytes, Charsets.ISO_8859_1)
}

internal data class PdfRef(val number: Int, val generation: Int)

internal class PdfDict(private val entries: Map<String, Any?>) {
    operator fun get(key: String): Any? = entries[key]

    fun name(key: String): String? = (entries[key] as? PdfName)?.value
}

/** A stream's dictionary and where its data is in the file; the data is read only when needed. */
internal class PdfStream(val dict: PdfDict, val dataStart: Long, val length: Int)

internal class PdfSyntaxException(message: String) : Exception(message)

internal fun Any?.asNumber(): Double? =
    when (this) {
        is Long -> toDouble()
        is Double -> this
        else -> null
    }

internal fun Any?.asInt(): Int? =
    when (this) {
        is Long -> if (this in Int.MIN_VALUE..Int.MAX_VALUE) toInt() else null
        is Double -> if (this in -1e9..1e9) toInt() else null
        else -> null
    }

// ------------------------------------------------------------------------------------------ parser

/**
 * Reads PDF objects from [bytes], starting at [position]: the syntax of ISO 32000-1 §7.2–7.3,
 * forgiving where real files are sloppy (an odd hex string, a stray keyword), and bounded where a
 * damaged one could make it run away (nesting, string length).
 */
internal class PdfParser(private val bytes: PdfBytes, var position: Long = 0) {

    fun readObject(): Any? = readObject(depth = 0)

    /** `"<number> <generation> obj"` at the current position, or `null` if it is not there. */
    fun readObjectHeader(): PdfRef? {
        val start = position
        val number = readNonNegativeInt()
        val generation = number?.let { readNonNegativeInt() }
        if (number != null && generation != null && readKeyword() == "obj") {
            return PdfRef(number, generation)
        }
        position = start
        return null
    }

    /**
     * The next keyword (`obj`, `stream`, `xref`, `trailer`…), or `null` if something else is next.
     */
    fun readKeyword(): String? {
        skipSpace()
        val start = position
        while (isRegular(bytes[position])) position++
        if (position == start) return null
        val word = String(bytes.copy(start, (position - start).toInt()), Charsets.ISO_8859_1)
        if (word.first().isDigit() || word.first() in "+-.") {
            position = start
            return null
        }
        return word
    }

    fun readNonNegativeInt(): Int? {
        skipSpace()
        val start = position
        var value = 0L
        while (bytes[position] in '0'.code..'9'.code) {
            value = value * 10 + (bytes[position] - '0'.code)
            if (value > Int.MAX_VALUE) {
                position = start
                return null
            }
            position++
        }
        if (position == start || isRegular(bytes[position])) {
            position = start
            return null
        }
        return value.toInt()
    }

    fun skipSpace() {
        while (true) {
            val c = bytes[position]
            when {
                c < 0 -> return
                isWhite(c) -> position++
                c == '%'.code -> {
                    while (bytes[position].let { it >= 0 && it != '\n'.code && it != '\r'.code }) {
                        position++
                    }
                }
                else -> return
            }
        }
    }

    private fun readObject(depth: Int): Any? {
        if (depth > MAX_DEPTH) throw PdfSyntaxException("Nested too deeply at $position")
        skipSpace()
        return when (val c = bytes[position]) {
            -1 -> throw PdfSyntaxException("End of file")
            '/'.code -> readName()
            '('.code -> readLiteralString()
            '['.code -> readArray(depth)
            '<'.code -> if (bytes[position + 1] == '<'.code) readDict(depth) else readHexString()
            else ->
                if (c in '0'.code..'9'.code || c == '+'.code || c == '-'.code || c == '.'.code) {
                    readNumberOrRef()
                } else {
                    when (val keyword = readKeyword()) {
                        "true" -> true
                        "false" -> false
                        "null" -> null
                        else -> throw PdfSyntaxException("Unexpected '$keyword' at $position")
                    }
                }
        }
    }

    private fun readNumberOrRef(): Any {
        val start = position
        val number = readNumber()
        if (number is Long && number >= 0) {
            val afterFirst = position
            val generation = readNonNegativeInt()
            if (generation != null) {
                skipSpace()
                if (bytes[position] == 'R'.code && !isRegular(bytes[position + 1])) {
                    position++
                    if (number <= Int.MAX_VALUE) return PdfRef(number.toInt(), generation)
                }
            }
            position = afterFirst
        }
        if (position == start) throw PdfSyntaxException("Bad number at $start")
        return number
    }

    private fun readNumber(): Any {
        val start = position
        while (bytes[position].let { it in '0'.code..'9'.code || it in SIGNS_AND_POINT }) {
            position++
        }
        val text = String(bytes.copy(start, (position - start).toInt()), Charsets.ISO_8859_1)
        if (!text.contains('.'))
            text.toLongOrNull()?.let {
                return it
            }
        // Writers produce "--5", "5.", "-.5" and the like: read what can be read, as viewers do.
        return text.trimStart('+').replace("--", "-").toDoubleOrNull() ?: 0L
    }

    private fun readName(): PdfName {
        position++ // '/'
        val out = ByteArrayOutputStream()
        while (true) {
            val c = bytes[position]
            if (!isRegular(c)) break
            if (c == '#'.code) {
                val high = hexValue(bytes[position + 1])
                val low = hexValue(bytes[position + 2])
                if (high >= 0 && low >= 0) {
                    out.write(high * 16 + low)
                    position += 3
                    continue
                }
            }
            out.write(c)
            position++
        }
        return PdfName(String(out.toByteArray(), Charsets.ISO_8859_1))
    }

    private fun readLiteralString(): PdfString {
        position++ // '('
        val out = ByteArrayOutputStream()
        var nesting = 1
        while (out.size() < MAX_STRING_BYTES) {
            val c = bytes[position++]
            when (c) {
                -1 -> break
                '('.code -> {
                    nesting++
                    out.write(c)
                }
                ')'.code -> {
                    if (--nesting == 0) break
                    out.write(c)
                }
                '\\'.code -> readEscape(out)
                else -> out.write(c)
            }
        }
        return PdfString(out.toByteArray())
    }

    private fun readEscape(out: ByteArrayOutputStream) {
        when (val c = bytes[position++]) {
            'n'.code -> out.write('\n'.code)
            'r'.code -> out.write('\r'.code)
            't'.code -> out.write('\t'.code)
            'b'.code -> out.write('\b'.code)
            'f'.code -> out.write(0x0C)
            '\r'.code -> if (bytes[position] == '\n'.code) position++ // a line continued
            '\n'.code -> Unit
            in '0'.code..'7'.code -> {
                var value = c - '0'.code
                repeat(2) {
                    val next = bytes[position]
                    if (next in '0'.code..'7'.code) {
                        value = value * 8 + (next - '0'.code)
                        position++
                    }
                }
                out.write(value and 0xFF)
            }
            -1 -> Unit
            else -> out.write(c) // \( \) \\ and any other character stand for themselves
        }
    }

    private fun readHexString(): PdfString {
        position++ // '<'
        val out = ByteArrayOutputStream()
        var high = -1
        while (out.size() < MAX_STRING_BYTES) {
            val c = bytes[position++]
            if (c == -1 || c == '>'.code) break
            val value = hexValue(c)
            if (value < 0) continue
            if (high < 0) {
                high = value
            } else {
                out.write(high * 16 + value)
                high = -1
            }
        }
        if (high >= 0) out.write(high * 16) // an odd last digit is followed by a 0
        return PdfString(out.toByteArray())
    }

    private fun readArray(depth: Int): List<Any?> {
        position++ // '['
        val items = ArrayList<Any?>()
        while (true) {
            skipSpace()
            when (bytes[position]) {
                ']'.code -> {
                    position++
                    return items
                }
                -1 -> throw PdfSyntaxException("Unterminated array")
            }
            items += readObject(depth + 1)
        }
    }

    private fun readDict(depth: Int): PdfDict {
        position += 2 // '<<'
        val entries = HashMap<String, Any?>()
        while (true) {
            skipSpace()
            val c = bytes[position]
            if (c == '>'.code && bytes[position + 1] == '>'.code) {
                position += 2
                return PdfDict(entries)
            }
            if (c != '/'.code) throw PdfSyntaxException("Expected a key at $position")
            val key = readName().value
            entries[key] = readObject(depth + 1)
        }
    }

    companion object {
        private const val MAX_DEPTH = 64
        private const val MAX_STRING_BYTES = 1 shl 20
        private val SIGNS_AND_POINT = intArrayOf('+'.code, '-'.code, '.'.code)

        fun isWhite(c: Int): Boolean = c == 0 || c == 9 || c == 10 || c == 12 || c == 13 || c == 32

        fun isDelimiter(c: Int): Boolean =
            c == '('.code ||
                c == ')'.code ||
                c == '<'.code ||
                c == '>'.code ||
                c == '['.code ||
                c == ']'.code ||
                c == '{'.code ||
                c == '}'.code ||
                c == '/'.code ||
                c == '%'.code

        fun isRegular(c: Int): Boolean = c >= 0 && !isWhite(c) && !isDelimiter(c)

        private fun hexValue(c: Int): Int =
            when (c) {
                in '0'.code..'9'.code -> c - '0'.code
                in 'a'.code..'f'.code -> c - 'a'.code + 10
                in 'A'.code..'F'.code -> c - 'A'.code + 10
                else -> -1
            }
    }
}
