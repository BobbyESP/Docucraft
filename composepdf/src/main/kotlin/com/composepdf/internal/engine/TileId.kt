/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.engine

/**
 * Identity of a tile, packed into a single [Long].
 *
 * Layout (most to least significant): 22 bits page index, 6 bits level (biased by [LEVEL_BIAS] so
 * negative levels pack cleanly), 18 bits tile column, 18 bits tile row.
 *
 * A packed primitive key removes the string building/parsing that the previous engine used for
 * every cache lookup and makes tile sets allocation-free to compare.
 */
@JvmInline
internal value class TileId(val packed: Long) {
    val pageIndex: Int
        get() = ((packed ushr 42) and 0x3FFFFF).toInt()

    val level: Int
        get() = (((packed ushr 36) and 0x3F).toInt()) - LEVEL_BIAS

    val x: Int
        get() = ((packed ushr 18) and 0x3FFFF).toInt()

    val y: Int
        get() = (packed and 0x3FFFF).toInt()

    override fun toString(): String = "TileId(page=$pageIndex level=$level x=$x y=$y)"

    companion object {
        private const val LEVEL_BIAS = 16

        fun of(pageIndex: Int, level: Int, x: Int, y: Int): TileId {
            require(pageIndex in 0..0x3FFFFF) { "pageIndex out of range: $pageIndex" }
            val biasedLevel = level + LEVEL_BIAS
            require(biasedLevel in 0..0x3F) { "level out of range: $level" }
            require(x in 0..0x3FFFF) { "x out of range: $x" }
            require(y in 0..0x3FFFF) { "y out of range: $y" }
            return TileId(
                (pageIndex.toLong() shl 42) or
                    (biasedLevel.toLong() shl 36) or
                    (x.toLong() shl 18) or
                    y.toLong()
            )
        }
    }
}
