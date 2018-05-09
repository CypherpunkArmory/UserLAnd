package tech.userland.userland.database.repositories

import android.content.Context
import org.jetbrains.anko.db.*
import tech.userland.userland.database.database
import tech.userland.userland.database.models.Filesystem

class FilesystemRepository(val context: Context) {

    private val parser = rowParser { filesystemId: Int, name: String, realRoot: Int, location: String, type: String, dateCreated: String ->
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
        return context.database.use {
            insert(Filesystem.TABLE_NAME,
                    "name" to filesystem.name,
                    "realRoot" to filesystem.realRoot,
                    "location" to filesystem.location,
                    "type" to filesystem.type,
                    "dateCreated" to filesystem.dateCreated
            )
        }
    }
}