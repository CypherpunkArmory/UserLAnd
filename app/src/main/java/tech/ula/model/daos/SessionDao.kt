package tech.ula.model.daos

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import tech.ula.model.entities.Session

@Dao
interface SessionDao {
    @Query("update session set active = 0")
    fun resetSessionActivity()

    @Query("select * from Session")
    fun getAllSessions(): LiveData<List<Session>>

    @Query("select * from session where name = :name")
    fun getSessionByName(name: String): Session

    @Query("select * from session where name = :appName and isAppsSession = 1")
    fun findAppsSession(appName: String): List<Session>

    @Query("select * from session where active = 1")
    fun findActiveSessions(): LiveData<List<Session>>

    // TODO test
    @Query("select * from session where active = 1 and isAppsSession = 1")
    fun findActiveAppsSessions(): LiveData<List<Session>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertSession(session: Session)

    @Query("delete from session where id = :id")
    fun deleteSessionById(id: Long)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateSession(session: Session)

    @Query("update session " +
            "set " +
            "filesystemName = (select filesystem.name from filesystem " +
            "where filesystem.id = session.filesystemId)")
    fun updateFilesystemNamesForAllSessions()
}