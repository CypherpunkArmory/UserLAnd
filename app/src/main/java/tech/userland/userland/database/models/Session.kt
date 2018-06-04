package tech.userland.userland.database.models

import android.arch.persistence.room.Entity
import android.arch.persistence.room.ForeignKey
import android.arch.persistence.room.Index
import android.arch.persistence.room.PrimaryKey
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
@Entity(tableName = "session",
        foreignKeys = [ForeignKey(
                entity = Filesystem::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("filesystemId"),
                onDelete = ForeignKey.CASCADE)],
        indices = [
            Index(value = ["name"], unique = true),
            Index(value = ["filesystemId"])
        ])
data class Session(
        @PrimaryKey(autoGenerate = true)
        val id: Long,
        var name: String = "",
        var filesystemId: Long,
        var filesystemName: String = "",
        var active: Boolean = false,
        var username: String = "",
        var password: String = "",
        var serviceType: String = "",
        var clientType: String = "",
        val port: Long = 2022,
        var pid: Long = 0,
        val startupScript: String = "",
        val runAtDeviceStartup: String = "",
        val initialCommand: String = ""
) : Parcelable