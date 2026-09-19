/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain

/**
 * How the document list is ordered.
 *
 * Carries no labels and no icons: what an ordering is called, and which arrow stands for it, are
 * presentation decisions. See `SortOptionUi` for those.
 */
data class SortOption(val criteria: Criteria, val order: Order) {

    enum class Criteria {
        DATE,
        NAME,
        SIZE,
    }

    enum class Order {
        ASC,
        DESC;

        fun reverse(): Order =
            when (this) {
                ASC -> DESC
                DESC -> ASC
            }
    }

    companion object {
        val DateAsc = SortOption(Criteria.DATE, Order.ASC)
        val DateDesc = SortOption(Criteria.DATE, Order.DESC)
        val NameAsc = SortOption(Criteria.NAME, Order.ASC)
        val NameDesc = SortOption(Criteria.NAME, Order.DESC)
        val SizeAsc = SortOption(Criteria.SIZE, Order.ASC)
        val SizeDesc = SortOption(Criteria.SIZE, Order.DESC)
    }
}
