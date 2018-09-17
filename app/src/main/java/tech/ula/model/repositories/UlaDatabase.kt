package tech.ula.model.repositories

import android.arch.persistence.db.SupportSQLiteDatabase
import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.arch.persistence.room.migration.Migration
import android.content.Context
import tech.ula.model.daos.AppsDao
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.daos.FilesystemDao
import tech.ula.model.daos.SessionDao
import tech.ula.model.entities.App

@Database(entities = [Session::class, Filesystem::class, App::class], version = 3, exportSchema = true)
abstract class UlaDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun filesystemDao(): FilesystemDao
    abstract fun appsDao(): AppsDao

    companion object {

        @Volatile
        private var INSTANCE: UlaDatabase? = null

        fun getInstance(context: Context): UlaDatabase =
                INSTANCE ?: synchronized(this) {
                    INSTANCE
                            ?: buildDatabase(context).also { INSTANCE = it }
                }

        private fun buildDatabase(context: Context) =
                Room.databaseBuilder(context.applicationContext,
                        UlaDatabase::class.java, "Data.db")
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
        database.execSQL("CREATE TABLE apps (" +
                "name TEXT NOT NULL, " +
                "category TEXT NOT NULL, " +
                "filesystemRequired TEXT NOT NULL, " +
                "supportsCli INTEGER NOT NULL, " +
                "supportsGui INTEGER NOT NULL, " +
                "isPaidApp INTEGER NOT NULL, " +
                "version INTEGER NOT NULL, " +
                "PRIMARY KEY(`name`))")

        database.execSQL("CREATE UNIQUE INDEX index_apps_name ON apps (name)")

        database.execSQL("ALTER TABLE filesystem ADD COLUMN isAppsFilesystem INTEGER NOT NULL DEFAULT 0")

        database.execSQL("DROP INDEX index_session_name")
        database.execSQL("ALTER TABLE session ADD COLUMN isAppsSession INTEGER NOT NULL DEFAULT 0")
    }
}