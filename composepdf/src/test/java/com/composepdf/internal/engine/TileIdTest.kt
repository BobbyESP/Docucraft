/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TileIdTest {

    @Test
    fun roundTrips_allFields() {
        val cases =
            listOf(
                intArrayOf(0, 0, 0, 0),
                intArrayOf(1, 2, 3, 4),
                intArrayOf(1999, -4, 130, 250),
                intArrayOf(0x3FFFFF, 16, 0x3FFFF, 0x3FFFF),
                intArrayOf(42, -8, 0, 7),
            )
        for ((page, level, x, y) in cases) {
            val id = TileId.of(page, level, x, y)
            assertEquals(page, id.pageIndex)
            assertEquals(level, id.level)
            assertEquals(x, id.x)
            assertEquals(y, id.y)
        }
    }

    @Test
    fun distinctTiles_haveDistinctPackedValues() {
        val a = TileId.of(1, 2, 3, 4)
        val b = TileId.of(1, 2, 4, 3)
        val c = TileId.of(1, 3, 3, 4)
        val d = TileId.of(2, 2, 3, 4)
        assertNotEquals(a.packed, b.packed)
        assertNotEquals(a.packed, c.packed)
        assertNotEquals(a.packed, d.packed)
    }

    @Test
    fun outOfRange_throws() {
        assertThrows(IllegalArgumentException::class.java) { TileId.of(-1, 0, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { TileId.of(0, 48, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { TileId.of(0, -17, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { TileId.of(0, 0, 0x40000, 0) }
    }
}
