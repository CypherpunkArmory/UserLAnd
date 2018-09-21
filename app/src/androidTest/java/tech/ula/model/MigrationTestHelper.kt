package tech.ula.model

import android.content.ContentValues

class MigrationTestHelper {
    fun getVersion1Filesystem(id: Long, name: String): ContentValues {
        val filesystemValues = ContentValues()
        filesystemValues.put("id", id)
        filesystemValues.put("name", name)
        filesystemValues.put("distributionType", "dummy")
        filesystemValues.put("archType", "dummy")
        filesystemValues.put("defaultUsername", "dummy")
        filesystemValues.put("defaultPassword", "dummy")
        filesystemValues.put("location", "dummy")
        filesystemValues.put("dateCreated", "dummy")
        filesystemValues.put("realRoot", 0)
        return filesystemValues
    }

    fun getVersion1Session(id: Long, name: String): ContentValues {
        val sessionValues = ContentValues()
        sessionValues.put("id", id)
        sessionValues.put("name", name)
        sessionValues.put("filesystemId", 1)
        sessionValues.put("filesystemName", "firstFs")
        sessionValues.put("active", 0)
        sessionValues.put("username", "dummy")
        sessionValues.put("password", "dummy")
        sessionValues.put("geometry", "dummy")
        sessionValues.put("serviceType", "dummy")
        sessionValues.put("clientType", "dummy")
        sessionValues.put("port", 0)
        sessionValues.put("pid", 0)
        sessionValues.put("startupScript", "dummy")
        sessionValues.put("runAtDeviceStartup", 0)
        sessionValues.put("initialCommand", "dummy")
        return sessionValues
    }

    fun getVersion2Filesystem(id: Long, name: String): ContentValues {
        val filesystemValues = ContentValues()
        filesystemValues.put("id", id)
        filesystemValues.put("name", name)
        filesystemValues.put("distributionType", "dummy")
        filesystemValues.put("archType", "dummy")
        filesystemValues.put("defaultUsername", "dummy")
        filesystemValues.put("defaultPassword", "dummy")
        filesystemValues.put("location", "dummy")
        filesystemValues.put("dateCreated", "dummy")
        filesystemValues.put("realRoot", 0)
        filesystemValues.put("isDownloaded", 0)
        return filesystemValues
    }

    fun getVersion2Session(id: Long, name: String): ContentValues {
        val sessionValues = ContentValues()
        sessionValues.put("id", id)
        sessionValues.put("name", name)
        sessionValues.put("filesystemId", 1)
        sessionValues.put("filesystemName", "firstFs")
        sessionValues.put("active", 0)
        sessionValues.put("username", "dummy")
        sessionValues.put("password", "dummy")
        sessionValues.put("vncPassword", "dummy")
        sessionValues.put("geometry", "dummy")
        sessionValues.put("serviceType", "dummy")
        sessionValues.put("clientType", "dummy")
        sessionValues.put("port", 0)
        sessionValues.put("pid", 0)
        sessionValues.put("startupScript", "dummy")
        sessionValues.put("runAtDeviceStartup", 0)
        sessionValues.put("initialCommand", "dummy")
        return sessionValues
    }

    fun getVersion3Filesystem(id: Long, name: String): ContentValues {
        val filesystemValues = ContentValues()
        filesystemValues.put("id", id)
        filesystemValues.put("name", name)
        filesystemValues.put("distributionType", "dummy")
        filesystemValues.put("archType", "dummy")
        filesystemValues.put("defaultUsername", "dummy")
        filesystemValues.put("defaultPassword", "dummy")
        filesystemValues.put("defaultVncPassword", "dummy")
        filesystemValues.put("location", "dummy")
        filesystemValues.put("dateCreated", "dummy")
        filesystemValues.put("realRoot", 0)
        filesystemValues.put("isDownloaded", 0)
        return filesystemValues
    }
}