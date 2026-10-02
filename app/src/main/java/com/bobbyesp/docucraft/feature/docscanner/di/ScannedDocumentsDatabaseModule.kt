/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.di

import com.bobbyesp.docucraft.feature.docscanner.data.db.DocumentsDatabase
import com.bobbyesp.docucraft.feature.docscanner.data.db.dao.DocumentDao
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val scannedDocumentsDatabaseModule = module {
    // How the catalogue is built, migrations included, is the database's own business: the tests
    // build theirs the same way.
    single<DocumentsDatabase> { DocumentsDatabase.builder(androidContext()).build() }

    single<DocumentDao> { get<DocumentsDatabase>().documentDao() }
}
