package tech.ula.model.entities

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
    var vncPassword: String = "",
    var serviceType: String = "",
    var port: Long = 2022,
    var pid: Long = 0,
    val isAppsSession: Boolean = false
) : Parcelable