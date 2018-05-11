package tech.userland.userland.database.repositories

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.widget.Toast
import org.jetbrains.anko.db.*
import tech.userland.userland.database.database
import tech.userland.userland.database.models.Filesystem

class FilesystemRepository(val context: Context) {

    private val parser = rowParser { filesystemId: Int,
                                     name: String,
                                     realRoot: Int,
                                     location: String,
                                     type: String,
                                     dateCreated: String ->

        val realRootBool = (realRoot == 1)
        Filesystem(name, realRootBool, location, type, dateCreated)
    }

    fun getAllFilesystems(): ArrayList<Filesystem> {
        return ArrayList(context.database.use {
            select(Filesystem.TABLE_NAME).exec {
                parseList(parser)
            }
        })
    }

    fun insertFilesystem(filesystem: Filesystem) {
        try {
            return context.database.use {
                insertOrThrow(Filesystem.TABLE_NAME,
                        "name" to filesystem.name,
                        "realRoot" to filesystem.realRoot,
                        "location" to filesystem.location,
                        "type" to filesystem.type,
                        "dateCreated" to filesystem.dateCreated
                )
            }
        }
        catch (error: SQLiteConstraintException) {
            Toast.makeText(context, "Filesystem name exists. Names must be unique.", Toast.LENGTH_LONG).show()
        }
    }

    fun updateFilesystem(filesystem: Filesystem) {

    }

    fun deleteFilesystem(filesystem: Filesystem) {
        return context.database.use {
            delete(Filesystem.TABLE_NAME, "name = {name}", "name" to filesystem.name)
        }
    }

    fun deleteFilesystemByName(name: String) {
        return context.database.use {
            delete(Filesystem.TABLE_NAME, "name = {name}", "name" to name)
        }
    }
}