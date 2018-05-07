package tech.userland.userland.database.entity

import android.arch.persistence.room.Entity
import android.arch.persistence.room.ForeignKey
import android.arch.persistence.room.PrimaryKey

@Entity(tableName = "sessions",
        foreignKeys = arrayOf(ForeignKey(
        entity = SessionEntity::class,
        parentColumns = arrayOf("filesystemId"),
        childColumns = arrayOf("sessionId"),
        onDelete = ForeignKey.CASCADE)))
data class SessionEntity(
        @PrimaryKey(autoGenerate = true)
        val sessionId: Long,

        val filesystemId: Long,
        val name: String = "",
        val active: Boolean = false,
        val type: String = ""
)