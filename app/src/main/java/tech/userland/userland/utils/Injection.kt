package tech.userland.userland.utils

import android.content.Context
import tech.userland.userland.database.AppDatabase
import tech.userland.userland.database.repositories.FilesystemDao
import tech.userland.userland.database.repositories.SessionDao

fun provideSessionDao(context: Context): SessionDao {
    val database = AppDatabase.getInstance(context)
    return database.sessionDao()
}

fun provideFilesystemDao(context: Context): FilesystemDao {
    val database = AppDatabase.getInstance(context)
    return database.filesystemDao()
}