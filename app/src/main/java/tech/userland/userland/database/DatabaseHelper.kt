package tech.userland.userland.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.jetbrains.anko.db.*
import tech.userland.userland.database.models.Filesystem
import tech.userland.userland.database.models.Session

class DatabaseOpenHelper(context: Context): ManagedSQLiteOpenHelper(context, "AppDatabase") {
    companion object {
        private var instance: DatabaseOpenHelper? = null

        @Synchronized
        fun getInstance(context: Context): DatabaseOpenHelper {
            if(instance == null) {
                instance = DatabaseOpenHelper(context.applicationContext)
            }
            return instance!!
        }
    }

    override fun onCreate(database: SQLiteDatabase) {
        database.createTable(Filesystem.TABLE_NAME, true,
                Filesystem.COLUMN_FILESYSTEM_ID to INTEGER + PRIMARY_KEY + AUTOINCREMENT,
                Filesystem.COLUMN_NAME to TEXT,
                Filesystem.COLUMN_REAL_ROOT to INTEGER,
                Filesystem.COLUMN_LOCATION to TEXT,
                Filesystem.COLUMN_TYPE to TEXT,
                Filesystem.COLUMN_DATE_CREATED to TEXT
        )

        database.createTable(Session.TABLE_NAME, true,
                Session.COLUMN_SESSION_ID to INTEGER + PRIMARY_KEY + AUTOINCREMENT,
                Session.COLUMN_NAME to TEXT,
                Session.COLUMN_FILESYSTEM_ID to INTEGER,
                Session.COLUMN_INITIAL_COMMAND to TEXT,
                Session.COLUMN_RUN_AT_DEVICE_STARTUP to TEXT,
                Session.COLUMN_STARTUP_SCRIPT to TEXT,
                Session.COLUMN_PID to INTEGER,
                Session.COLUMN_ACTIVE to INTEGER,
                Session.COLUMN_TYPE to TEXT,
                FOREIGN_KEY(Session.COLUMN_FILESYSTEM_ID, Filesystem.TABLE_NAME, Filesystem.COLUMN_FILESYSTEM_ID)
                )
//        database.createTable("Session", true,
//                "sessionId" to INTEGER + PRIMARY_KEY + AUTOINCREMENT,
//                "name" to TEXT,
//                "filesystemId" to INTEGER,
//                "initialCommand" to TEXT,
//                "runAtDeviceStartup" to TEXT,
//                "startupScript" to TEXT,
//                "pid" to INTEGER,
//                "active" to INTEGER,
//                "type" to TEXT,
//                FOREIGN_KEY("filesystemId", "Filesystem","filesystemId"))
    }

    // TODO don't just drop tables on upgrade
    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        database.dropTable(Filesystem.TABLE_NAME, ifExists = true)
        database.dropTable(Session.TABLE_NAME, ifExists = true)
    }
}


val Context.database: DatabaseOpenHelper
    get() = DatabaseOpenHelper.getInstance(applicationContext)