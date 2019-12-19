package tech.ula.model.repositories

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tech.ula.model.daos.AppsDao
import tech.ula.model.daos.FilesystemDao
import tech.ula.model.daos.SessionDao
import tech.ula.model.entities.App
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session

@Database(entities = [Session::class, Filesystem::class, App::class], version = 8, exportSchema = true)
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
                        .addMigrations(
                                Migration1To2(),
                                Migration2To3(),
                                Migration3To4(),
                                Migration4To5(),
                                Migration5To6(),
                                Migration6To7(),
                                Migration7To8()
                        )
                        .addCallback(object : RoomDatabase.Callback() {
                            override fun onOpen(db: SupportSQLiteDatabase) {
                                super.onOpen(db)
                                // Since this should only be called when the app is restarted, all
                                // all child processes should have been killed and sessions should be
                                // inactive.
                                GlobalScope.launch { getInstance(context).sessionDao().resetSessionActivity() }
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
        database.execSQL("CREATE TABLE filesystem(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, distributionType TEXT NOT NULL, archType TEXT NOT NULL, defaultUsername TEXT NOT NULL, defaultPassword TEXT NOT NULL, defaultVncPassword TEXT NOT NULL, isAppsFilesystem INTEGER NOT NULL, lastUpdated INTEGER NOT NULL DEFAULT -1);")
        database.execSQL("INSERT INTO filesystem SELECT id, name, distributionType, archType, defaultUsername, defaultPassword, defaultVncPassword, isAppsFilesystem, -1 FROM filesystem_backup;")
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

class Migration4To5 : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE session ADD COLUMN geometry TEXT NOT NULL DEFAULT ''")
    }
}

class Migration5To6 : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE filesystem ADD COLUMN isCreatedFromBackup INTEGER NOT NULL DEFAULT 0")
    }
}

class Migration6To7 : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("PRAGMA foreign_keys=off;")
        database.execSQL("BEGIN TRANSACTION;")

        // Filesystem fields: remove lastUpdated, add versionCodeUsed
        database.execSQL("CREATE TEMPORARY TABLE filesystem_backup(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, distributionType TEXT NOT NULL, archType TEXT NOT NULL, defaultUsername TEXT NOT NULL, defaultPassword TEXT NOT NULL, defaultVncPassword TEXT NOT NULL, isAppsFilesystem INTEGER NOT NULL, isCreatedFromBackup INTEGER NOT NULL);")
        database.execSQL("INSERT INTO filesystem_backup SELECT id, name, distributionType, archType, defaultUsername, defaultPassword, defaultVncPassword, isAppsFilesystem, isCreatedFromBackup FROM filesystem;")
        database.execSQL("DROP TABLE filesystem;")
        database.execSQL("CREATE TABLE filesystem(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, distributionType TEXT NOT NULL, archType TEXT NOT NULL, defaultUsername TEXT NOT NULL, defaultPassword TEXT NOT NULL, defaultVncPassword TEXT NOT NULL, isAppsFilesystem INTEGER NOT NULL, isCreatedFromBackup INTEGER NOT NULL, versionCodeUsed TEXT NOT NULL DEFAULT 'v0.0.0');")
        database.execSQL("INSERT INTO filesystem SELECT id, name, distributionType, archType, defaultUsername, defaultPassword, defaultVncPassword, isAppsFilesystem, isCreatedFromBackup, 'v0.0.0' FROM filesystem_backup;")
        database.execSQL("DROP TABLE filesystem_backup;")

        database.execSQL("COMMIT;")
        database.execSQL("PRAGMA foreign_keys_on")
    }
}

class Migration7To8 : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE session ADD COLUMN serviceLocation TEXT NOT NULL DEFAULT 'local'")
        database.execSQL("ALTER TABLE session ADD COLUMN ip TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE session ADD COLUMN filesystemType TEXT NOT NULL DEFAULT ''")

        database.execSQL("ALTER TABLE apps ADD COLUMN supportsLocal INTEGER NOT NULL DEFAULT 1")
        database.execSQL("ALTER TABLE apps ADD COLUMN supportsRemote INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE apps ADD COLUMN serviceType TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE apps ADD COLUMN serviceLocation TEXT NOT NULL DEFAULT ''")

        database.execSQL("ALTER TABLE filesystem ADD COLUMN location TEXT NOT NULL DEFAULT 'local'")

        var cursor = database.query("SELECT * FROM apps")
        try {
            while (cursor.moveToNext()) {
                var appName = cursor.getString(cursor.getColumnIndex("name"))
                var cursor2 =
                    database.query("SELECT * FROM session WHERE name = '$appName' and isAppsSession = 1 LIMIT 1")
                try {
                    cursor2.moveToNext()
                    var serviceType = cursor2.getString(cursor2.getColumnIndex("serviceType"))
                    database.execSQL("UPDATE apps SET serviceType = '$serviceType' WHERE name = '$appName'")
                } catch (t: Throwable) {
                    // do nothing
                } finally {
                    cursor2.close()
                }
            }
        } catch (t: Throwable) {
            // do nothing
        } finally {
            cursor.close()
        }
    }
}