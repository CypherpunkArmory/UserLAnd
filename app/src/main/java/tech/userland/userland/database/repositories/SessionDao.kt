package tech.userland.userland.database.repositories

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.*
import tech.userland.userland.database.models.Session

@Dao
interface SessionDao {
    @Query("select * from Session")
    fun getAllSessions(): LiveData<List<Session>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSession(session: Session)

    @Query("delete from session where id = :id")
    fun deleteSessionById(id: Long)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateSession(session: Session)
}