package tech.userland.userland.database

import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.content.Context
import tech.userland.userland.database.models.Filesystem
import tech.userland.userland.database.models.Session
import tech.userland.userland.database.repositories.FilesystemDao
import tech.userland.userland.database.repositories.SessionDao

// TODO export schema appropriately
@Database(entities = arrayOf(Session::class, Filesystem::class), version = 1, exportSchema = false)
abstract class Database : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun filesystemDao(): FilesystemDao

    companion object {


        @Volatile
        private var INSTANCE: Database? = null

        fun getInstance(context: Context): Database =
                INSTANCE ?: synchronized(this) {
                    INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
                }

        private fun buildDatabase(context: Context) =
                Room.databaseBuilder(context.applicationContext,
                        Database::class.java, "Data.db")
                        .build()
    }
}