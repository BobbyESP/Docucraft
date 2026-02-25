package com.bobbyesp.docucraft.core.data.local.db

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Update
import androidx.room.Upsert

/**
 * Base Data Access Object (DAO) interface for performing common database operations.
 * This interface provides generic methods for inserting, updating, deleting, and upserting
 * entities in a Room database.
 *
 * @param T The type of the entity that this DAO will manage.
 */
interface BaseDao<T> {

    /**
     * Inserts an entity into the database. If there is a conflict, the existing entity
     * will be replaced.
     *
     * @param entity The entity to be inserted.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: T)

    /**
     * Inserts a list of entities into the database. If any entity already exists, it will
     * be replaced.
     *
     * @param entities The list of entities to be inserted.
     * @return A list of row IDs for the inserted entities.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<T>): List<Long>

    /**
     * Updates an existing entity in the database.
     *
     * @param entity The entity to be updated.
     */
    @Update
    suspend fun update(entity: T)

    /**
     * Deletes an entity from the database.
     *
     * @param entity The entity to be deleted.
     */
    @Delete
    suspend fun delete(entity: T)

    /**
     * Inserts an entity if it does not exist, or updates it if it already exists.
     *
     * @param entity The entity to be upserted.
     */
    @Upsert
    suspend fun upsert(entity: T)
}
