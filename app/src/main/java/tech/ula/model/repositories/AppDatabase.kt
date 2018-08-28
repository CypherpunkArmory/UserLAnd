package tech.ula.model.repositories

import android.arch.persistence.db.SupportSQLiteDatabase
import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.arch.persistence.room.migration.Migration
import android.content.Context
import tech.ula.model.daos.ApplicationDao
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.daos.FilesystemDao
import tech.ula.model.daos.SessionDao
import tech.ula.model.entities.Application

@Database(entities = [Session::class, Filesystem::class, Application::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun filesystemDao(): FilesystemDao
    abstract fun applicationDao(): ApplicationDao

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
                        .addMigrations(Migration1To2(), Migration2To3())
                        .build()
    }
}

class Migration1To2 : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE filesystem ADD COLUMN isDownloaded INTEGER NOT NULL DEFAULT 0")

        database.execSQL("ALTER TABLE session ADD COLUMN isExtracted INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE session ADD COLUMN lastUpdated INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE session ADD COLUMN bindings TEXT NOT NULL DEFAULT ''")
    }
}

class Migration2To3 : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE application (" +
                "name TEXT NOT NULL, " +
                "id INTEGER NOT NULL, " +
                "category TEXT NOT NULL, " +
                "supportsCli INTEGER NOT NULL, " +
                "supportsGui INTEGER NOT NULL, " +
                "script TEXT NOT NULL, " +
                "icon TEXT NOT NULL, " +
                "remoteTimestamp INTEGER NOT NULL, " +
                "isPaidApplication INTEGER NOT NULL, " +
                "userHasPurchased INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))")

        database.execSQL("CREATE UNIQUE INDEX index_application_name ON application (name)")
    }
}