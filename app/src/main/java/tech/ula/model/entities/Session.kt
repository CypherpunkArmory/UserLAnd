package tech.ula.model.entities

import android.annotation.SuppressLint
import android.arch.persistence.room.Entity
import android.arch.persistence.room.ForeignKey
import android.arch.persistence.room.Index
import android.arch.persistence.room.PrimaryKey
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
@SuppressLint("ParcelCreator")
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
    var password: String = "userland",
    var geometry: String = "1024x768",
    var serviceType: String = "",
    var clientType: String = "",
    var port: Long = 2022,
    var pid: Long = 0,
    val startupScript: String = "",
    val runAtDeviceStartup: Boolean = false,
    val initialCommand: String = "",
    var isExtracted: Boolean = false,
    var lastUpdated: Long = 0,
    var bindings: String = ""
) : Parcelable