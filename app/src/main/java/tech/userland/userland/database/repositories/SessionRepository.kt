package tech.userland.userland.database.repositories

import android.content.Context
import org.jetbrains.anko.db.*
import tech.userland.userland.database.database
import tech.userland.userland.database.models.Session

class SessionRepository(val context: Context) {

    private val parser = rowParser { sessionId: Int, name: String, filesystemId: Int, initialCommand: String, runAtDeviceStartup: String, startupScript: String, pid: Int, active: Int, type: String ->
        val activeBool = (active == 1)
        Session(name, filesystemId, initialCommand, runAtDeviceStartup, startupScript, pid, activeBool, type)
    }

    fun getAllSessions(): ArrayList<Session> {
        return ArrayList(context.database.use {
            select(Session.TABLE_NAME).exec {
                parseList(parser)
            }
        })
    }

    fun insertSession(session: Session) {
        return context.database.use {
            insert(Session.TABLE_NAME,
                    "name" to session.name,
                    "filesystemID" to session.filesystemId,
                    "initialCommand" to session.initialCommand,
                    "runAtDeviceStartup" to session.runAtDeviceStartup,
                    "startupScript" to session.startupScript,
                    "pid" to session.pid,
                    "active" to session.active,
                    "type" to session.type
            )
        }
    }
}