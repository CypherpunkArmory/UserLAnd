package tech.userland.userland.database.models

data class Session(val name: String,
                   val filesystemId: Int,
                   val filesystemName: String,
                   val username: String,
                   val password: String,
                   val port: Int,
                   val initialCommand: String,
                   val runAtDeviceStartup: String,
                   val startupScript: String,
                   val pid: Int,
                   var active: Boolean,
                   val type: String) {
    companion object {
        val TABLE_NAME = "Session"
        val COLUMN_SESSION_ID = "sessionId"
        val COLUMN_NAME = "name"
        val COLUMN_FILESYSTEM_ID = "filesystemId"
        val COLUMN_FILESYSTEM_NAME = "filesystemName"
        val COLUMN_USERNAME = "username"
        val COLUMN_PASSWORD = "password"
        val COLUMN_PORT = "port"
        val COLUMN_INITIAL_COMMAND = "initialCommand"
        val COLUMN_RUN_AT_DEVICE_STARTUP = "runAtDeviceStartup"
        val COLUMN_STARTUP_SCRIPT = "startupScript"
        val COLUMN_PID = "pid"
        val COLUMN_ACTIVE = "active"
        val COLUMN_TYPE = "type"
    }
}