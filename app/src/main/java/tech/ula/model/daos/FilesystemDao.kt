package tech.ula.model.daos

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.ServiceLocation

@Dao
interface FilesystemDao {
    @Query("select * from filesystem")
    fun getAllFilesystems(): LiveData<List<Filesystem>>

    @Query("select * from filesystem where name = :name")
    fun getFilesystemByName(name: String): Filesystem

    @Query("select * from filesystem where isAppsFilesystem = 1 and distributionType = :requiredFilesystemType and location = :requiredFilesystemLocation")
    fun findAppsFilesystemByType(requiredFilesystemType: String, requiredFilesystemLocation: String): List<Filesystem>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertFilesystem(filesystem: Filesystem): Long

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateFilesystem(filesystem: Filesystem)

    @Query("delete from filesystem where id = :id")
    fun deleteFilesystemById(id: Long)
}