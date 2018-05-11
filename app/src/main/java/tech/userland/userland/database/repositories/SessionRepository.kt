package tech.userland.userland.database.repositories

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.widget.Toast
import org.jetbrains.anko.db.*
import tech.userland.userland.database.database
import tech.userland.userland.database.models.Filesystem
import tech.userland.userland.database.models.Session

class SessionRepository(val context: Context) {

    private val parser = rowParser {
        sessionId: Int,
        name: String,
        filesystemId: Int,
        username: String,
        password: String,
        initialCommand: String,
        runAtDeviceStartup: String,
        startupScript: String,
        pid: Int,
        active: Int,
        type: String ->

        val activeBool = (active == 1)
        Session(name, filesystemId, username, password, initialCommand, runAtDeviceStartup, startupScript, pid, activeBool, type)
    }

    fun getAllSessions(): ArrayList<Session> {
        return ArrayList(context.database.use {
            select(Session.TABLE_NAME).exec {
                parseList(parser)
            }
        })
    }

    fun insertSession(session: Session) {
        try {
            return context.database.use {
                insertOrThrow(Session.TABLE_NAME,
                        "name" to session.name,
                        "filesystemID" to session.filesystemId,
                        "username" to session.username,
                        "password" to session.password,
                        "initialCommand" to session.initialCommand,
                        "runAtDeviceStartup" to session.runAtDeviceStartup,
                        "startupScript" to session.startupScript,
                        "pid" to session.pid,
                        "active" to session.active,
                        "type" to session.type
                )
            }
        }
        catch (error: SQLiteConstraintException) {
            Toast.makeText(context, "Session name exists. Names must be unique.", Toast.LENGTH_LONG).show()
        }
    }

    fun updateSessionActive(session: Session) {
        return context.database.use {
            update(Session.TABLE_NAME, "active" to session.active)
                    .whereArgs("name = {name}", "name" to session.name)
                    .exec()
        }
    }

    fun deleteSessionByName(name: String) {
        return context.database.use {
            delete(Session.TABLE_NAME, "name = {name}", "name" to name)
        }
    }
}