package tech.userland.userland.database.models

import android.arch.persistence.room.Entity
import android.arch.persistence.room.Index
import android.arch.persistence.room.PrimaryKey
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import java.util.*

@Parcelize
@Entity(tableName = "filesystem", indices = [(Index(value = ["name"], unique = true))])
data class Filesystem(
        @PrimaryKey(autoGenerate = true)
        val id: Long,
        val name: String = "",
        val type: String = "",
        val realRoot: Boolean = false,
        val location: String = "",
        val dateCreated: String = Date().toString()
) : Parcelable