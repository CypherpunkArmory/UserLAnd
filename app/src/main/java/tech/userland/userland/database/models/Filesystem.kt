package tech.userland.userland.database.models

data class Filesystem(val name: String,
                      val realRoot: Boolean,
                      val location: String,
                      val type: String,
                      val dateCreated: String) {
    companion object {
        val TABLE_NAME = "Filesystem"
        val COLUMN_FILESYSTEM_ID = "filesystemId"
        val COLUMN_NAME = "name"
        val COLUMN_REAL_ROOT  = "realRoot"
        val COLUMN_LOCATION = "location"
        val COLUMN_TYPE = "type"
        val COLUMN_DATE_CREATED = "dateCreated"
    }
}