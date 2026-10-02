/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.repository

import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Tag
import kotlinx.coroutines.flow.Flow

/** How renaming a tag ended. As with folders, each is something the user can cause. */
sealed interface TagChange {
    data object Done : TagChange

    /** Another tag already has that name, however it is capitalized or accented. */
    data object NameTaken : TagChange

    data object NameEmpty : TagChange

    data object NotFound : TagChange
}

/**
 * The tags, and which documents carry them. Only documents the app keeps are tagged; deleting a tag
 * removes it from its documents and changes nothing else about them.
 */
interface TagsRepository {

    /** Every tag, by name. */
    fun observeTags(): Flow<List<Tag>>

    /** The tags of one document, by name. */
    fun observeTagsOf(documentUuid: String): Flow<List<Tag>>

    /** The library's documents that carry every one of [tagUuids], newest first. */
    fun observeDocumentsWithAll(tagUuids: List<String>): Flow<List<Document.Managed>>

    /**
     * The tag called [name], created if there is none. A name that only differs from an existing
     * tag's in case, accents or spaces is that tag: typing "facturas " finds "Facturas".
     *
     * @param uuid The identity a new tag is given.
     * @return The tag, or `null` when [name] is empty.
     */
    suspend fun getOrCreate(uuid: String, name: String): Tag?

    suspend fun rename(uuid: String, name: String): TagChange

    /** @param color A key of the palette, or `null` for the default. */
    suspend fun setColor(uuid: String, color: String?)

    suspend fun delete(uuid: String)

    /** Puts the tag on the document. Nothing changes if it is already there. */
    suspend fun tag(documentUuid: String, tagUuid: String)

    suspend fun untag(documentUuid: String, tagUuid: String)

    /**
     * Which tags have a section of their own in Home, and in what order: those of [tagUuids], in
     * that order. Every other tag has none. Written at once, so no two sections share a place.
     */
    suspend fun setHomeSections(tagUuids: List<String>)
}
