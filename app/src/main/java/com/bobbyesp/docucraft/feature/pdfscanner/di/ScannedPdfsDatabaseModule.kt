package com.bobbyesp.docucraft.feature.pdfscanner.di

import androidx.room.Room
import com.bobbyesp.docucraft.feature.pdfscanner.data.local.db.PdfDatabase
import com.bobbyesp.docucraft.feature.pdfscanner.data.local.db.dao.ScannedPdfDao
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val scannedPdfsDatabaseModule = module {
    single<PdfDatabase> {
        Room.databaseBuilder(androidContext(), PdfDatabase::class.java, "scanned_pdfs.db").build()
    }

    single<ScannedPdfDao> { get<PdfDatabase>().scannedPdfDao() }
}
