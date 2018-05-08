package tech.userland.userland.database.models

import java.util.Date

data class Filesystem(val filesystemId: Int,
                      val realRoot: Boolean,
                      val location: String,
                      val type: String,
                      val dateCreated: Date) {
    companion object {
        val TABLE_NAME = "Filesystem"
        val COLUMN_FILESYSTEM_ID = "filesystemId"
        val COLUMN_REAL_ROOT  = "realRoot"
        val COLUMN_LOCATION = "location"
        val COLUMN_TYPE = "type"
        val COLUMN_DATE_CREATED = "dateCreated"
    }
}