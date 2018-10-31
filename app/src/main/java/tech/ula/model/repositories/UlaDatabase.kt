package tech.ula.model.repositories

import android.arch.persistence.db.SupportSQLiteDatabase
import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.arch.persistence.room.migration.Migration
import android.content.Context
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.launch
import tech.ula.model.daos.AppsDao
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.daos.FilesystemDao
import tech.ula.model.daos.SessionDao
import tech.ula.model.entities.App

@Database(entities = [Session::class, Filesystem::class, App::class], version = 4, exportSchema = true)
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

        private fun buildDatabase(context: Context): UlaDatabase =
                Room.databaseBuilder(context.applicationContext,
                        UlaDatabase::class.java, "Data.db")
                        .addMigrations(Migration1To2(), Migration2To3(), Migration3To4())
                        .addCallback(object : RoomDatabase.Callback() {
                            override fun onOpen(db: SupportSQLiteDatabase) {
                                super.onOpen(db)
                                // Since this should only be called when the app is restarted, all
                                // all child processes should have been killed and sessions should be
                                // inactive.
                                launch(CommonPool) { getInstance(context).sessionDao().resetSessionActivity() }
                            }
                        })
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

        database.execSQL("DROP INDEX index_filesystem_name")
        database.execSQL("ALTER TABLE filesystem ADD COLUMN isAppsFilesystem INTEGER NOT NULL DEFAULT 0")

        database.execSQL("DROP INDEX index_session_name")
        database.execSQL("ALTER TABLE session ADD COLUMN isAppsSession INTEGER NOT NULL DEFAULT 0")

        database.execSQL("ALTER TABLE filesystem ADD COLUMN defaultVncPassword TEXT NOT NULL DEFAULT 'userland'")
        database.execSQL("ALTER TABLE session ADD COLUMN vncPassword TEXT NOT NULL DEFAULT 'userland'")
    }
}

class Migration3To4 : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("PRAGMA foreign_keys=off;")
        database.execSQL("BEGIN TRANSACTION;")

        // Remove filesystem fields realRoot, dateCreated, location, isDownloaded.
        database.execSQL("CREATE TEMPORARY TABLE filesystem_backup(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, distributionType TEXT NOT NULL, archType TEXT NOT NULL, defaultUsername TEXT NOT NULL, defaultPassword TEXT NOT NULL, defaultVncPassword TEXT NOT NULL, isAppsFilesystem INTEGER NOT NULL);")
        database.execSQL("INSERT INTO filesystem_backup SELECT id, name, distributionType, archType, defaultUsername, defaultPassword, defaultVncPassword, isAppsFilesystem FROM filesystem;")
        database.execSQL("DROP TABLE filesystem;")
        database.execSQL("CREATE TABLE filesystem(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, distributionType TEXT NOT NULL, archType TEXT NOT NULL, defaultUsername TEXT NOT NULL, defaultPassword TEXT NOT NULL, defaultVncPassword TEXT NOT NULL, isAppsFilesystem INTEGER NOT NULL);")
        database.execSQL("INSERT INTO filesystem SELECT id, name, distributionType, archType, defaultUsername, defaultPassword, defaultVncPassword, isAppsFilesystem FROM filesystem_backup;")
        database.execSQL("DROP TABLE filesystem_backup;")

        // Remove session fields geometry, clientType, startupScript, runAtDeviceStartup, initialCommand, isExtracted, lastUpdated, bindings
        database.execSQL("CREATE TEMPORARY TABLE session_backup(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, filesystemId INTEGER NOT NULL, filesystemName TEXT NOT NULL, active INTEGER NOT NULL, username TEXT NOT NULL, password TEXT NOT NULL, vncPassword TEXT NOT NULL, serviceType TEXT NOT NULL, port INTEGER NOT NULL, pid INTEGER NOT NULL, isAppsSession INTEGER NOT NULL, FOREIGN KEY(filesystemId) REFERENCES filesystem(id))")
        database.execSQL("INSERT INTO session_backup SELECT id, name, filesystemId, filesystemName, active, username, password, vncPassword, serviceType, port, pid, isAppsSession FROM session;")
        database.execSQL("DROP TABLE session;")
        database.execSQL("CREATE TABLE session(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, filesystemId INTEGER NOT NULL, filesystemName TEXT NOT NULL, active INTEGER NOT NULL, username TEXT NOT NULL, password TEXT NOT NULL, vncPassword TEXT NOT NULL, serviceType TEXT NOT NULL, port INTEGER NOT NULL, pid INTEGER NOT NULL, isAppsSession INTEGER NOT NULL, FOREIGN KEY(filesystemId) REFERENCES filesystem(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        database.execSQL("INSERT INTO session SELECT id, name, filesystemId, filesystemName, active, username, password, vncPassword, serviceType, port, pid, isAppsSession FROM session_backup;")
        database.execSQL("DROP TABLE session_backup;")
        database.execSQL("CREATE INDEX index_session_filesystemId ON session (filesystemId)")

        database.execSQL("COMMIT;")
        database.execSQL("PRAGMA foreign_keys_on")
    }
}