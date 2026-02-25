package com.bobbyesp.docucraft.feature.docscanner.di

import androidx.room.Room
import com.bobbyesp.docucraft.feature.docscanner.data.db.DocumentsDatabase
import com.bobbyesp.docucraft.feature.docscanner.data.db.dao.ScannedDocumentDao
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val scannedDocumentsDatabaseModule = module {
    single<DocumentsDatabase> {
        Room.databaseBuilder(androidContext(), DocumentsDatabase::class.java, "scanned_pdfs.db")
            .build()
    }

    single<ScannedDocumentDao> { get<DocumentsDatabase>().scannedDocumentDao() }
}
