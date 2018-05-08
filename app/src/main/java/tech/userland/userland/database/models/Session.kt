package tech.userland.userland.database.models

import org.jetbrains.anko.db.FOREIGN_KEY

data class Session(val sessionId: Int,
                   val filesystemId: Int,
                   val initialCommand: String,
                   val runAtDeviceStartup: String,
                   val pid: Int,
                   val active: Boolean,
                   val type: String) {
    companion object {
        val TABLE_NAME = "Session"
        val COLUMN_SESSION_ID = "sessionId"
        val COLUMN_FILESYSTEM_ID = FOREIGN_KEY(
                "filesystemId",
                "Filesystem",
                "filesystemId")
        val COLUMN_INITIAL_COMMAND = "initialCommand"
        val COLUMN_RUN_AT_DEVICE_STARTUP = "runAtDeviceStartup"
        val COLUMN_STARTUP_SCRIPT = "startupScript"
        val COLUMN_PID = "pid"
        val COLUMN_ACTIVE = "active"
        val COLUMN_TYPE = "type"
    }
}