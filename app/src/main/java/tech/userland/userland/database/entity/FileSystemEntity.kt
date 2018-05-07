package tech.userland.userland.database.entity

import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey

@Entity
data class FilesystemEntity(
        @PrimaryKey(autoGenerate = true)
        val filesystemId: Long,

        val name: String = "",
        val type: String = ""
)