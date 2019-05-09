package tech.ula.model.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
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
    var geometry: String = "",
    val isAppsSession: Boolean = false
) : Parcelable {
    override fun toString(): String {
        return "Session(id=$id, name=$name, filesystemId=$filesystemId, filesystemName=" +
                "$filesystemName, active=$active, serviceType=$serviceType, port=$port, pid=" +
                "$pid, isAppsSession=$isAppsSession)"
    }
}