package tech.ula.model.entities

import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import java.util.Date

@Parcelize
@Entity(tableName = "filesystem")
data class Filesystem(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    var name: String = "",
    var distributionType: String = "",
    var archType: String = "",
    var defaultUsername: String = "user",
    var defaultPassword: String = "userland",
    var defaultVncPassword: String = "userland",
    val isAppsFilesystem: Boolean = false,
    val location: String = "",
    val dateCreated: String = Date().toString(),
    val realRoot: Boolean = false,
    var isDownloaded: Boolean = false
) : Parcelable