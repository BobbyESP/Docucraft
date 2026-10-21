/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.widgets

/**
 * The home-screen widgets that show the library.
 *
 * A widget is a picture the launcher keeps: it does not follow the library, and has to be told when
 * what it shows is no longer true.
 */
interface HomeScreenWidgets {

    /**
     * Has every widget that shows the library drawn again from what the library holds now. Does
     * nothing when the user has placed none, and never fails: a widget that could not be reached
     * keeps what it was showing.
     */
    fun refresh()
}
