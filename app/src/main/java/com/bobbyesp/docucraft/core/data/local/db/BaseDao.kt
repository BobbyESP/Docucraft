/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.data.local.db

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Update

/**
 * The writes every DAO needs.
 *
 * Deliberately small: a base type is a place for what is actually shared, not a catalogue of
 * everything Room can do.
 *
 * @param T The entity this DAO manages.
 */
interface BaseDao<T> {

    /** Inserts [entity], replacing any row it conflicts with. */
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: T)

    /** Updates the row matching the primary key of [entity]. */
    @Update suspend fun update(entity: T)
}
