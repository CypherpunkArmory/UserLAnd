package tech.ula.model.daos

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import tech.ula.model.entities.App

@Dao
interface AppsDao {
    @Query("select * from apps")
    fun getAllApps(): LiveData<List<App>>

    @Query("select * from apps where name = :name")
    fun getAppByName(name: String): App

    // TODO test
    // TODO add preferredServiceType to apps and join with session.serviceType
    @Query("select apps.* from apps inner join session on apps.name = session.name and session.active = 1")
    fun getActiveApps(): LiveData<List<App>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertApp(application: App)
}