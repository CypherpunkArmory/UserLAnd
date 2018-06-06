package tech.ula.database.repositories

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.*
import tech.ula.database.models.Filesystem

@Dao
interface FilesystemDao {
    @Query("select * from filesystem")
    fun getAllFilesystems(): LiveData<List<Filesystem>>

    @Query("select * from filesystem where name = :name")
    fun getFilesystemByName(name: String): Filesystem

    @Insert(onConflict = OnConflictStrategy.FAIL)
    fun insertFilesystem(filesystem: Filesystem)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateFilesystem(filesystem: Filesystem)

    @Query("delete from filesystem where id = :id")
    fun deleteFilesystemById(id: Long)
}