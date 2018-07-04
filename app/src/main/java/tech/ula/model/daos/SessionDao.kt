package tech.ula.model.daos

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.*
import tech.ula.model.entities.Session

@Dao
interface SessionDao {
    @Query("select * from Session")
    fun getAllSessions(): LiveData<List<Session>>

    @Query("select * from session where name = :name")
    fun getSessionByName(name: String): Session

    @Insert(onConflict = OnConflictStrategy.FAIL)
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