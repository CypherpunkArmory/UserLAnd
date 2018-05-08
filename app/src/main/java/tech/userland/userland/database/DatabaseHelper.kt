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
                instance = DatabaseOpenHelper(context.getApplicationContext())
            }
            return instance!!
        }
    }

    override fun onCreate(database: SQLiteDatabase) {
        database.createTable(Filesystem.TABLE_NAME, true,
                Filesystem.COLUMN_FILESYSTEM_ID to INTEGER + PRIMARY_KEY + UNIQUE,
                Filesystem.COLUMN_REAL_ROOT to INTEGER,
                Filesystem.COLUMN_LOCATION to TEXT,
                Filesystem.COLUMN_TYPE to TEXT,
                Filesystem.COLUMN_DATE_CREATED to TEXT
        )

        database.createTable(Session.TABLE_NAME, true,
                Session.COLUMN_SESSION_ID to INTEGER + PRIMARY_KEY + UNIQUE,
                Session.COLUMN_FILESYSTEM_ID,
                Session.COLUMN_INITIAL_COMMAND to TEXT,
                Session.COLUMN_RUN_AT_DEVICE_STARTUP to TEXT,
                Session.COLUMN_STARTUP_SCRIPT to TEXT,
                Session.COLUMN_PID to INTEGER,
                Session.COLUMN_ACTIVE to INTEGER,
                Session.COLUMN_TYPE to TEXT
                )
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

val Context.database: DatabaseOpenHelper
    get() = DatabaseOpenHelper.getInstance(applicationContext)