package tech.ula.model.entities

import android.arch.persistence.room.Entity
import android.arch.persistence.room.Index
import android.arch.persistence.room.PrimaryKey
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
@Entity(tableName = "apps", indices = [(Index(value = ["name"], unique = true))])
data class App(
    @PrimaryKey()
    val name: String,
    var category: String = "",
    var supportsCli: Boolean = false,
    var supportsGui: Boolean = false,
    var isPaidApp: Boolean = false,
    var version: Long = 0
) : Parcelable