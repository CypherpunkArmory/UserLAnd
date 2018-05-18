package tech.userland.userland.database.models

import android.arch.persistence.room.Entity
import android.arch.persistence.room.ForeignKey
import android.arch.persistence.room.PrimaryKey

@Entity(tableName = "session",
        foreignKeys = arrayOf(ForeignKey(
                entity = Filesystem::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("id"),
                onDelete = ForeignKey.CASCADE)))
data class Session(
        @PrimaryKey(autoGenerate = true)
        val id: Long,
        val name: String = "",
        val filesystemId: Long,
        val filesystemName: String = "",
        val username: String = "",
        val password: String = "",
        val port: Long,
        var active: Boolean = false,
        val type: String = "",
        val initialCommand: String = "",
        val runAtDeviceStartup: String = "",
        val startupScript: String = "",
        val pid: Long
)