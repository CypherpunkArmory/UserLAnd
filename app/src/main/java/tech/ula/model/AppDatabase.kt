package tech.ula.model

import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.content.Context
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.daos.FilesystemDao
import tech.ula.model.daos.SessionDao

// TODO export schema appropriately
@Database(entities = [Session::class, Filesystem::class], version = 1, exportSchema = false)
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