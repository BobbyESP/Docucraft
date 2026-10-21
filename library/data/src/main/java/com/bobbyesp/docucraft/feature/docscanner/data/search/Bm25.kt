/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.search

import kotlin.math.ln

/**
 * Scores how well a row of a full-text index matches a query, with Okapi BM25, from what FTS4's
 * `matchinfo(table, 'pcnalx')` reports about the row.
 *
 * FTS4 has no ranking function of its own, unlike FTS5. It does keep everything BM25 needs: how
 * often each term occurs in each column of the row, in how many rows it occurs at all, and how long
 * the row is against the average. The score is worked out here from those numbers.
 *
 * In short, a row scores higher when it has the query's terms more often, when those terms are rare
 * in the index, and when the row is short, since a term in a three-word title says more than the
 * same term in a three-page text.
 */
internal object Bm25 {

    /** How quickly repeating a term stops adding to the score. */
    private const val K1 = 1.2

    /** How much a long row is held back against a short one: 0 not at all, 1 fully. */
    private const val B = 0.75

    /**
     * @param matchInfo What `matchinfo(table, 'pcnalx')` returned for the row, as 32-bit integers:
     *   the number of terms `p` and of columns `c`, the number of rows `n`, the average length of
     *   each column `a`, the length of each column in this row `l`, and for every term and column
     *   three counts `x`: occurrences in this row, occurrences in all rows, and rows it occurs in.
     * @param weights How much a match in each column counts, in the table's column order.
     */
    fun score(matchInfo: IntArray, weights: DoubleArray): Double {
        val terms = matchInfo[0]
        val columns = matchInfo[1]
        val rows = matchInfo[2].toDouble()
        val averageLengths = 3
        val lengths = averageLengths + columns
        val counts = lengths + columns
        require(weights.size == columns) { "${weights.size} weights for $columns columns" }

        var score = 0.0
        for (term in 0 until terms) {
            for (column in 0 until columns) {
                val weight = weights[column]
                val at = counts + 3 * (term * columns + column)
                val inThisRow = matchInfo[at].toDouble()
                if (weight == 0.0 || inThisRow == 0.0) continue
                val rowsWithIt = matchInfo[at + 2].toDouble()

                // Rarer terms tell documents apart better. The form that never goes negative:
                // with the classic one, a term found in most rows would lower the score of the
                // rows that have it.
                val rarity = ln(1.0 + (rows - rowsWithIt + 0.5) / (rowsWithIt + 0.5))

                val average = matchInfo[averageLengths + column].toDouble()
                val relativeLength =
                    if (average > 0.0) matchInfo[lengths + column] / average else 1.0
                val frequency =
                    inThisRow * (K1 + 1.0) / (inThisRow + K1 * (1.0 - B + B * relativeLength))

                score += weight * rarity * frequency
            }
        }
        return score
    }
}
