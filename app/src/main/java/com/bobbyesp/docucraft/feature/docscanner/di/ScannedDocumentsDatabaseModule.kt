package com.bobbyesp.docucraft.feature.docscanner.di

import androidx.room.Room
import com.bobbyesp.docucraft.feature.docscanner.data.db.DocumentsDatabase
import com.bobbyesp.docucraft.feature.docscanner.data.db.DocumentsDatabaseMigrations.MIGRATION_2_3
import com.bobbyesp.docucraft.feature.docscanner.data.db.dao.ScannedDocumentDao
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val scannedDocumentsDatabaseModule = module {
    single<DocumentsDatabase> {
        Room.databaseBuilder(
            context = androidContext(),
            klass = DocumentsDatabase::class.java,
            name = "scanned_pdfs.db"
        ).addMigrations(
            MIGRATION_2_3
        )
            .build()
    }

    single<ScannedDocumentDao> { get<DocumentsDatabase>().scannedDocumentDao() }
}
