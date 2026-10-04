/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.maintenance

/**
 * What keeps the library in order without anybody asking: emptying the bin of what has been there
 * too long, and checking that the catalogue and the files agree. It runs now and then in the
 * background, whether the app is open or not.
 */
interface LibraryMaintenance {
    /**
     * Makes sure the upkeep is scheduled. Asking again changes nothing: what is already scheduled
     * keeps its turn.
     */
    fun schedule()
}
