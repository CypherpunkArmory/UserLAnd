package tech.ula.model

import android.arch.persistence.db.SupportSQLiteDatabase
import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.arch.persistence.room.migration.Migration
import android.content.Context
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.daos.FilesystemDao
import tech.ula.model.daos.SessionDao

// TODO export schema appropriately
@Database(entities = [Session::class, Filesystem::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun filesystemDao(): FilesystemDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
                INSTANCE ?: synchronized(this) {
                    INSTANCE
                            ?: buildDatabase(context).also { INSTANCE = it }
                }

        private fun buildDatabase(context: Context) =
                Room.databaseBuilder(context.applicationContext,
                        AppDatabase::class.java, "Data.db")
                        .build()
    }
}

class Migration1To2 : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
//        database.execSQL(
//                "PRAGMA foreign_keys=off; " +
//
//                "BEGIN TRANSACTION; " +
//
//                "ALTER TABLE filesystem RENAME TO _filesystem_old " +
//
//                "CREATE TABLE filesystem ( " +
//                "( id INTEGER PRIMARY KEY AUTOINCREMENT, " +
//                "name TEXT NOT NULL, " +
//                "distributionType TEXT NOT NULL, " +
//                "archType TEXT NOT NULL, " +
//                "defaultUsername TEXT NOT NULL, " +
//                "defaultPassword TEXT NOT NULL, " +
//                "location TEXT NOT NULL, " +
//                "dateCreated INTEGER NOT NULL DEFAULT 0 " +
//                "realRoot INTEGER NOT NULL " +
//                ");" +
//
//                "INSERT INTO filesystem " +
//                "(id, name, distributionType, archType, defaultUsername, defaultPassword, location, dateCreated, realRoot " +
//                "SELECT " +
//                 "id, name, distributionType, archType, defaultUsername, defaultPassword, location, dateCreated, realRoot " +
//                "FROM _filesystem_old; " +
//
//                "INSERT INTO filesystem " +
//                "(dateCreated) " +
//                 "VALUE " +
//                 "${System.currentTimeMillis()} " +
//
//                "COMMIT; " +
//
//                "PRAGMA foreign_keys=on;")
        database.execSQL("ALTER TABLE filesystem ADD COLUMN isDownloaded INTEGER NOT NULL DEFAULT 0")

        database.execSQL("ALTER TABLE session ADD COLUMN isExtracted INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE session ADD COLUMN lastUpdated INTEGER NOT NULL DEFAULT 0")
    }
}