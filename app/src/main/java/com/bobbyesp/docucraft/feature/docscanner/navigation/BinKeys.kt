/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The bin: the documents that were deleted and can still be brought back. */
@Serializable data object Bin : NavKey

/** What can be done to a document of the bin: restore it, or delete it for good. */
@Serializable data class BinDocumentActions(val documentUuid: String) : NavKey

/** Confirming that a document of the bin is deleted for good. */
@Serializable data class DeleteForever(val documentUuid: String) : NavKey

/** Confirming that everything in the bin is deleted for good. */
@Serializable data object EmptyBin : NavKey
