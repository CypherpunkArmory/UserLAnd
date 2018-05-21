package tech.userland.userland.database.models

import android.arch.persistence.room.Entity
import android.arch.persistence.room.ForeignKey
import android.arch.persistence.room.PrimaryKey
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
@Entity(tableName = "session",
        foreignKeys = [ForeignKey(
                entity = Filesystem::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("filesystemId"),
                onDelete = ForeignKey.CASCADE)])
data class Session(
        @PrimaryKey(autoGenerate = true)
        val id: Long,
        var name: String = "",
        var filesystemId: Long,
        var filesystemName: String = "",
        var username: String = "",
        var password: String = "",
        val port: Long = 2022,
        var active: Boolean = false,
        var type: String = "",
        val initialCommand: String = "",
        val runAtDeviceStartup: String = "",
        val startupScript: String = "",
        val pid: Long = 0
) : Parcelable