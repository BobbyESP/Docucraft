package com.bobbyesp.docucraft.data.local.db

import android.net.Uri
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.bobbyesp.docucraft.data.local.db.daos.SavedPDFsDao
import com.bobbyesp.docucraft.data.local.db.entity.SavedPdfEntity

const val CURRENT_VERSION = 1

@Database(
    entities = [SavedPdfEntity::class],
    version = CURRENT_VERSION,
    exportSchema = true,
    autoMigrations = [
    ]
)
@TypeConverters(UriConverters::class)
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

class UriConverters {
    @TypeConverter
    fun fromString(value: String?): Uri? {
        return if (value == null) null else Uri.parse(value)
    }

    @TypeConverter
    fun toString(uri: Uri?): String? {
        return uri?.toString()
    }
}