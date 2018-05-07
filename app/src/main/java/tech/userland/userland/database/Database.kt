package tech.userland.userland.database

import android.arch.persistence.room.Database
import android.arch.persistence.room.RoomDatabase
import tech.userland.userland.database.dao.*
import tech.userland.userland.database.entity.*

@Database(entities = arrayOf(SessionEntity::class, FilesystemEntity::class), version = 1, exportSchema = false)
abstract class Database : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun filesystemDao(): FilesystemDao
}