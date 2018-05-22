package tech.userland.userland.database.repositories

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy
import android.arch.persistence.room.Query
import tech.userland.userland.database.models.Filesystem

@Dao
interface FilesystemDao {
    @Query("select * from filesystem")
    fun getAllFilesystems(): LiveData<List<Filesystem>>

    @Query("select * from filesystem where name = :name")
    fun getFilesystemByName(name: String): Filesystem

    @Insert(onConflict = OnConflictStrategy.FAIL)
    fun insertFilesystem(filesystem: Filesystem)

    @Query("delete from filesystem where id = :id")
    fun deleteFilesystemById(id: Long)
}