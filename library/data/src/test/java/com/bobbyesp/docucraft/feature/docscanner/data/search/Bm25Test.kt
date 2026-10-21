/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.search

import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The score against hand-built `matchinfo` arrays. What matters is not the numbers but what they do
 * to an order: which of two rows comes first, and why.
 */
class Bm25Test {

    /**
     * What `matchinfo(table, 'pcnalx')` reports for a row of a one-column table matched by one
     * term.
     */
    private fun oneTerm(
        rows: Int = 100,
        averageLength: Int = 10,
        length: Int = 10,
        inThisRow: Int = 1,
        rowsWithIt: Int = 5,
    ) = intArrayOf(1, 1, rows, averageLength, length, inThisRow, inThisRow * rowsWithIt, rowsWithIt)

    private val unweighted = doubleArrayOf(1.0)

    @Test
    fun `a row of average length with the term once scores the term's rarity`() {
        val score = Bm25.score(oneTerm(rows = 10, rowsWithIt = 3), unweighted)

        assertEquals(ln(1.0 + 7.5 / 3.5), score, 1e-9)
    }

    @Test
    fun `a term found more often in the row scores higher, but each repeat adds less`() {
        val once = Bm25.score(oneTerm(inThisRow = 1), unweighted)
        val twice = Bm25.score(oneTerm(inThisRow = 2), unweighted)
        val threeTimes = Bm25.score(oneTerm(inThisRow = 3), unweighted)

        assertTrue(twice > once)
        assertTrue(threeTimes > twice)
        assertTrue(threeTimes - twice < twice - once)
    }

    @Test
    fun `a term few rows have scores higher than one most rows have`() {
        val rare = Bm25.score(oneTerm(rowsWithIt = 2), unweighted)
        val common = Bm25.score(oneTerm(rowsWithIt = 60), unweighted)

        assertTrue(rare > common)
    }

    // The classic formula goes negative here, which would push a matching row below ones that do
    // not have the term at all.
    @Test
    fun `a term nearly every row has still adds to the score`() {
        assertTrue(Bm25.score(oneTerm(rows = 10, rowsWithIt = 10), unweighted) > 0.0)
    }

    // The same word says more in a three-word title than in a three-page text.
    @Test
    fun `a short row scores higher than a long one for the same match`() {
        val short = Bm25.score(oneTerm(averageLength = 100, length = 5), unweighted)
        val long = Bm25.score(oneTerm(averageLength = 100, length = 900), unweighted)

        assertTrue(short > long)
    }

    @Test
    fun `a column's weight scales what a match in it counts`() {
        val plain = Bm25.score(oneTerm(), doubleArrayOf(1.0))
        val weighted = Bm25.score(oneTerm(), doubleArrayOf(10.0))

        assertEquals(plain * 10, weighted, 1e-9)
    }

    @Test
    fun `a match counts by the column it is in`() {
        // One term, two columns, 20 rows; both columns of average length. The term is once in the
        // first column in one row, once in the second in the other.
        val inFirst = intArrayOf(1, 2, 20, 5, 5, 5, 5, 1, 4, 4, 0, 4, 4)
        val inSecond = intArrayOf(1, 2, 20, 5, 5, 5, 5, 0, 4, 4, 1, 4, 4)
        val titleThenDescription = doubleArrayOf(10.0, 4.0)

        assertTrue(
            Bm25.score(inFirst, titleThenDescription) > Bm25.score(inSecond, titleThenDescription)
        )
    }

    @Test
    fun `the scores of the terms add up`() {
        // Two terms, one column. Each is once in the row and in 5 of 100 rows.
        val both = intArrayOf(2, 1, 100, 10, 10, 1, 5, 5, 1, 5, 5)

        assertEquals(2 * Bm25.score(oneTerm(), unweighted), Bm25.score(both, unweighted), 1e-9)
    }

    @Test
    fun `a term the row does not have adds nothing`() {
        assertEquals(0.0, Bm25.score(oneTerm(inThisRow = 0), unweighted), 0.0)
    }

    // An index that has just been created reports no average length yet.
    @Test
    fun `a column with no average length is not divided by`() {
        val score = Bm25.score(oneTerm(averageLength = 0, length = 0), unweighted)

        assertTrue(score > 0.0 && !score.isNaN() && !score.isInfinite())
    }

    @Test
    fun `weights that are not one per column are a mistake`() {
        assertThrows(IllegalArgumentException::class.java) {
            Bm25.score(oneTerm(), doubleArrayOf(1.0, 2.0))
        }
    }
}
