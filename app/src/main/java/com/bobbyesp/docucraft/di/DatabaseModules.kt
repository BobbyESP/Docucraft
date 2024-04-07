package com.bobbyesp.docucraft.di

import androidx.room.Room
import com.bobbyesp.docucraft.App
import com.bobbyesp.docucraft.data.local.db.PdfsDatabase
import com.bobbyesp.docucraft.data.local.db.daos.SavedPDFsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

private const val SAVED_PDFS_DATABASE_NAME = "saved_pdfs_db"

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModules {
    @Provides
    fun provideSavedPDFsDatabase(): PdfsDatabase {
        return Room.databaseBuilder(
            App.context, PdfsDatabase::class.java, SAVED_PDFS_DATABASE_NAME
        ).build()
    }

    @Provides
    fun provide(db: PdfsDatabase): SavedPDFsDao = db.savedPdfsDao()

}