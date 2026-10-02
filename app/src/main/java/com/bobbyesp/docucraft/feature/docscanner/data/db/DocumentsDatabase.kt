/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.bobbyesp.docucraft.feature.docscanner.data.db.dao.DocumentDao
import com.bobbyesp.docucraft.feature.docscanner.data.db.dao.FolderDao
import com.bobbyesp.docucraft.feature.docscanner.data.db.dao.PageDao
import com.bobbyesp.docucraft.feature.docscanner.data.db.dao.SearchDao
import com.bobbyesp.docucraft.feature.docscanner.data.db.dao.TagDao
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.DocumentActivityEntity
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.DocumentEntity
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.DocumentFtsEntity
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.DocumentTagEntity
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.DocumentTextStatusView
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.FolderEntity
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.LibraryDocumentView
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.PageEntity
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.PageLayoutEntity
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.PageTextEntity
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.PageTextFtsEntity
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.TagEntity

const val CURRENT_VERSION = 5

/**
 * The catalogue: which documents there are, how they are organized, what they say and what the user
 * has done with them. The PDFs themselves are files; the database never holds their bytes.
 */
@Database(
    entities =
        [
            DocumentEntity::class,
            DocumentActivityEntity::class,
            FolderEntity::class,
            TagEntity::class,
            DocumentTagEntity::class,
            PageEntity::class,
            PageTextEntity::class,
            PageLayoutEntity::class,
            DocumentFtsEntity::class,
            PageTextFtsEntity::class,
        ],
    views = [LibraryDocumentView::class, DocumentTextStatusView::class],
    version = CURRENT_VERSION,
    exportSchema = true,
    autoMigrations =
        [AutoMigration(from = 1, to = 2, spec = DocumentsDatabaseMigrations.Migration1To2::class)],
)
abstract class DocumentsDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    abstract fun searchDao(): SearchDao

    abstract fun folderDao(): FolderDao

    abstract fun tagDao(): TagDao

    abstract fun pageDao(): PageDao

    companion object {
        /**
         * The file keeps the name it has had since the catalogue was a list of scans. Under any
         * other name the app would start on an empty database, next to the one with the documents.
         */
        const val FILE_NAME = "scanned_pdfs.db"

        /**
         * The catalogue as the app opens it: every migration, and the triggers a new database is
         * given. Tests build theirs from here too, so they run what the app runs.
         *
         * There is no destructive fallback, on purpose. A version nothing migrates from has to stop
         * the app, not empty the catalogue.
         */
        fun builder(context: Context, name: String = FILE_NAME): Builder<DocumentsDatabase> =
            Room.databaseBuilder(context, DocumentsDatabase::class.java, name)
                .addMigrations(*DocumentsDatabaseMigrations.ALL)
                .addCallback(DatabaseTriggers.CreateOnNewDatabase)
    }
}
