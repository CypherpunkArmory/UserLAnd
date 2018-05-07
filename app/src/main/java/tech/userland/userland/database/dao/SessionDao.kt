package tech.userland.userland.database.dao

import android.arch.persistence.room.*
import android.arch.persistence.room.OnConflictStrategy.REPLACE
import tech.userland.userland.database.entity.SessionEntity


@Dao
interface SessionDao {
    @Query("select * from sessions")
    fun getAllSessions: List<SessionEntity>

    @Query("select * from sessions where name = :name")
    fun findSessionByName(name: String): SessionEntity

    @Query("select * from sessions where sessionId = :id")
    fun findSessionById(id: Long): SessionEntity

    @Query("select * from sessions, filesystems " +
            "where sessions.filesystemId = filesystems.filesystemId " +
            "and filesystems.filesystemId = :id")
    fun findSessionsByFileSystemId(id: Long): List<SessionEntity>

    @Query("select * from sessions, filesystems " +
    "where sessions.filesystemId = filesystems.filesystemId " +
    "and filesystems.name = :name")
    fun findSessionsByFilesystemName(name: String): List<SessionEntity>

    @Insert(onConflict = REPLACE)
    fun insertSession(session: SessionEntity)

    @Update(onConflict = REPLACE)
    fun updateSession(session: SessionEntity)

    @Delete
    fun deleteSession(session: SessionEntity)
}