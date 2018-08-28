package tech.ula.model.daos

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy
import android.arch.persistence.room.Query
import tech.ula.model.entities.Application

@Dao
interface ApplicationDao {
    @Query("select * from application")
    fun getAllApplications(): LiveData<List<Application>>

    @Query("select * from application where name = :name")
    fun getApplicationByName(name: String): Application

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertApplication(application: Application)
}