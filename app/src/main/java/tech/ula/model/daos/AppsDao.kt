package tech.ula.model.daos

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy
import android.arch.persistence.room.Query
import tech.ula.model.entities.App

@Dao
interface AppsDao {
    @Query("select * from apps")
    fun getAllApps(): LiveData<List<App>>

    @Query("select * from apps where name = :name")
    fun getAppByName(name: String): App

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertApp(application: App)
}