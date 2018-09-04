package tech.ula.model.entities

import android.arch.persistence.room.Entity
import android.arch.persistence.room.Index
import android.arch.persistence.room.PrimaryKey

@Entity(tableName = "apps", indices = [(Index(value = ["name"], unique = true))])
data class App(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val name: String = "",
    var category: String = "",
    var supportsCli: Boolean = false,
    var supportsGui: Boolean = false,
    var script: String = "",
    var icon: String = "",
    var remoteTimestamp: Long = 0,
    var isPaidApplication: Boolean = false,
    var userHasPurchased: Boolean = false
)