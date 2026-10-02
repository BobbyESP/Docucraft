/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.model

import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import java.text.Normalizer

/**
 * A folder of the library. A document is in one folder at most, and a document in none is in the
 * root: the root is not a folder, it is the absence of one.
 *
 * Folders nest without a limit here. How deep the app lets the user go is the interface's decision.
 *
 * @property parentUuid The folder this one is in, or `null` in the root.
 * @property color A key of the app's palette, never a colour value, so it follows the theme. A key
 *   the app does not know is shown as the default.
 * @property icon A key of the app's icon set, for the same reason.
 * @property pinnedAtEpochMillis When it was pinned to Home, which is also where it goes among the
 *   pinned folders; `null` when it is not pinned.
 * @property sort How its contents are ordered, if the user chose.
 */
data class Folder(
    val uuid: String,
    val name: String,
    val parentUuid: String?,
    val color: String?,
    val icon: String?,
    val pinnedAtEpochMillis: Long?,
    val sort: SortOption?,
    val createdAtEpochMillis: Long,
) {
    val isPinned: Boolean
        get() = pinnedAtEpochMillis != null
}

/**
 * A tag. Unlike a folder, a document can have several.
 *
 * @property color A key of the app's palette.
 * @property homePosition The place of this tag's section in Home, or `null` when it has none.
 */
data class Tag(val uuid: String, val name: String, val color: String?, val homePosition: Int?)

/**
 * A name as it is compared with other names: without the spaces around it or repeated inside it, in
 * lower case and without accents. Two folders with the same parent, and two tags, cannot have names
 * that are the same once normalized: "Facturas", "facturas " and "FACTURAS" are one name.
 */
fun normalizedNameOf(name: String): String =
    Normalizer.normalize(name.trim().replace(Whitespace, " "), Normalizer.Form.NFD)
        .replace(Marks, "")
        .lowercase()

/** A name as it is kept and shown: as the user wrote it, without stray spaces. */
fun tidyNameOf(name: String): String = name.trim().replace(Whitespace, " ")

private val Whitespace = Regex("\\s+")
private val Marks = Regex("\\p{Mn}+")

/** The rules of the tree of folders that can be decided without looking anything up. */
object FolderTree {

    /**
     * Whether putting [folderUuid] inside the folder at the end of [pathToNewParent] would make a
     * folder contain itself. It would when the folder is its own new parent, or one of the new
     * parent's ancestors.
     *
     * @param pathToNewParent The uuids from the root down to the new parent, itself included. Empty
     *   for the root.
     */
    fun wouldContainItself(folderUuid: String, pathToNewParent: List<String>): Boolean =
        folderUuid in pathToNewParent

    /**
     * A name for [name] that none of [taken] has: itself when it is free, else "Name (2)", "Name
     * (3)" and so on. For a folder that lands among others by something the user did not ask for by
     * name, such as its parent being deleted.
     *
     * @param taken The normalized names already in use where the folder is going.
     */
    fun freeName(name: String, taken: Set<String>): String {
        if (normalizedNameOf(name) !in taken) return name
        var number = 2
        while (normalizedNameOf("$name ($number)") in taken) number++
        return "$name ($number)"
    }
}
