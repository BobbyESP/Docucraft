package com.bobbyesp.docucraft.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.bobbyesp.docucraft.data.local.db.daos.SavedPDFsDao

const val CURRENT_VERSION = 1

@Database(
    entities = [],
    version = CURRENT_VERSION,
    exportSchema = true,
    autoMigrations = [
    ]
)
@TypeConverters()
abstract class PdfsDatabase: RoomDatabase() {
    abstract fun savedPdfsDao(): SavedPDFsDao
}

//object Migrations {
//    val MIGRATION_2_3 = object : Migration(2, 3) {
//        override fun migrate(db: SupportSQLiteDatabase) {
//            // Define the SQL statements for the migration
//            db.execSQL("ALTER TABLE clipboard_notes ADD COLUMN title TEXT")
//        }
//    }
//}