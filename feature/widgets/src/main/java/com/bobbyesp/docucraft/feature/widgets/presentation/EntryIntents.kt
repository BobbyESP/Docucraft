/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.widgets.presentation

/*
 * What `MainActivity` can be asked from outside the app's own screens: by the home-screen widgets
 * today. The Activity is exported, so any app can send these too. None of them carries anything to
 * act on but a document's uuid, which is only ever looked up in the catalogue.
 */

/**
 * The class name of `MainActivity`, which receives these. A widget names it instead of referring to
 * the class: the widgets do not depend on the app module that holds it. It is never renamed anyway,
 * since launchers and the widgets people have placed know it by this name. `EntryActivityTest`
 * checks the two agree.
 */
const val ENTRY_ACTIVITY = "com.bobbyesp.docucraft.MainActivity"

/** Start a scan. */
const val ACTION_SCAN_DOCUMENT = "com.bobbyesp.docucraft.ACTION_SCAN_DOCUMENT"

/** Open the document named by [EXTRA_DOCUMENT_UUID]. */
const val ACTION_OPEN_DOCUMENT = "com.bobbyesp.docucraft.ACTION_OPEN_DOCUMENT"

/** Open the search of the library. */
const val ACTION_SEARCH_DOCUMENTS = "com.bobbyesp.docucraft.ACTION_SEARCH_DOCUMENTS"

const val EXTRA_DOCUMENT_UUID = "com.bobbyesp.docucraft.extra.DOCUMENT_UUID"
