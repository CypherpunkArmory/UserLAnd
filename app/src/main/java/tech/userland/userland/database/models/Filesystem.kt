package tech.userland.userland.database.models

import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey
import java.util.*

@Entity(tableName = "filesystem")
data class Filesystem(
        @PrimaryKey(autoGenerate = true)
        val id: Long,
        val name: String = "",
        val type: String = "",
        val realRoot: Boolean = false,
        val location: String = "",
        val dateCreated: Date = Date()
)