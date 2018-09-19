package tech.ula.model.daos

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy
import android.arch.persistence.room.Query
import android.arch.persistence.room.Update
import tech.ula.model.entities.Filesystem

@Dao
interface FilesystemDao {
    @Query("select * from filesystem")
    fun getAllFilesystems(): LiveData<List<Filesystem>>

    @Query("select * from filesystem where name = :name")
    fun getFilesystemByName(name: String): Filesystem

    @Query("select * from filesystem where isAppsFilesystem = 1 and distributionType = :requiredFilesystemType")
    fun findAppsFilesystemByType(requiredFilesystemType: String): List<Filesystem>

    @Insert(onConflict = OnConflictStrategy.FAIL)
    fun insertFilesystem(filesystem: Filesystem)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateFilesystem(filesystem: Filesystem)

    @Query("delete from filesystem where id = :id")
    fun deleteFilesystemById(id: Long)
}