package tech.ula.model.entities

import android.annotation.SuppressLint
import android.arch.persistence.room.Entity
import android.arch.persistence.room.Index
import android.arch.persistence.room.PrimaryKey
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import java.util.Date

@Parcelize
@SuppressLint("ParcelCreator")
@Entity(tableName = "filesystem", indices = [(Index(value = ["name"], unique = true))])
data class Filesystem(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    var name: String = "",
    var distributionType: String = "",
    var archType: String = "",
    val defaultUsername: String = "user",
    val defaultPassword: String = "userland",
    val location: String = "",
    val dateCreated: String = Date().toString(),
    val realRoot: Boolean = false,
    var isDownloaded: Boolean = false
) : Parcelable