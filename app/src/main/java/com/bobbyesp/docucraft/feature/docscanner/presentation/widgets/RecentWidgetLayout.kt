/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.widgets

/**
 * How many documents the recent-documents widget has room for.
 *
 * The widget does not scroll: it shows the rows that fit and no more. A list that scrolls on a home
 * screen has to be fed through a service the launcher binds to, kept alive by it and asked for one
 * row at a time; rows that are simply part of the widget are drawn with it, the same way on every
 * version of Android. The numbers are those of `widget_recents.xml` and `widget_recents_row.xml`.
 */
internal object RecentWidgetLayout {

    private const val PADDING_DP = 8f
    private const val HEADER_DP = 40f

    /** A row and the gap above it. */
    private const val ROW_DP = 56f

    /**
     * At least one, even where it does not quite fit: a widget with the header alone would look
     * broken, and a row cut by a few dp at the bottom does not.
     */
    fun rowsFor(heightDp: Float, most: Int): Int {
        val room = heightDp - 2 * PADDING_DP - HEADER_DP
        return (room / ROW_DP).toInt().coerceIn(1, most.coerceAtLeast(1))
    }
}
