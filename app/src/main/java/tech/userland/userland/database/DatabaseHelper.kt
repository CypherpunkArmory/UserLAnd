package tech.userland.userland.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.jetbrains.anko.db.*

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
        database.createTable("Filesystem", true,
                "filesystemId" to INTEGER + PRIMARY_KEY + UNIQUE,
                "realRoot" to INTEGER,
                "location" to TEXT,
                "type" to TEXT,
                "dateCreated" to TEXT
        )

        database.createTable("Session", true,
                "sessionId" to INTEGER + PRIMARY_KEY + UNIQUE,
                FOREIGN_KEY("filesystemId",
                        "Filesystem",
                        "filesystemId"),
                "initialCommand" to TEXT,
                "runAtDeviceStartup" to TEXT,
                "startupScript" to TEXT,
                "pid" to INTEGER,
                "active" to INTEGER,
                "type" to TEXT
        )
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}