/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/*
 * Organizing the library: folders and tags. As with the document's actions, every sheet and dialog
 * here is a key of the one back stack, and carries identities rather than what they identify.
 */

/** What a folder holds. The root of the library for no [folderUuid]. */
@Serializable data class FolderContents(val folderUuid: String? = null) : NavKey

/**
 * A folder's name, color and icon: of [folderUuid], or of a new folder inside [parentUuid] when
 * there is none.
 */
@Serializable
data class FolderEditor(val folderUuid: String? = null, val parentUuid: String? = null) : NavKey

/** What can be done to one folder. */
@Serializable data class FolderActions(val folderUuid: String) : NavKey

/** Confirming a folder's deletion. */
@Serializable data class DeleteFolder(val folderUuid: String) : NavKey

/** Choosing the folder a document, or a folder, goes into. One of the two is set. */
@Serializable
data class MoveToFolder(val documentUuid: String? = null, val folderUuid: String? = null) : NavKey

/** The tags of one document. */
@Serializable data class DocumentTags(val documentUuid: String) : NavKey

/** Every tag: renaming, coloring and deleting them, and choosing which have a section in Home. */
@Serializable data object ManageTags : NavKey

/** A tag's name and color: of [tagUuid], or of a new tag when there is none. */
@Serializable data class TagEditor(val tagUuid: String? = null) : NavKey

/** Confirming a tag's deletion. */
@Serializable data class DeleteTag(val tagUuid: String) : NavKey
