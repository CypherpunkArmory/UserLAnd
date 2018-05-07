package tech.userland.userland.database.dao

import android.arch.persistence.room.*
import android.arch.persistence.room.OnConflictStrategy.REPLACE
import tech.userland.userland.database.entity.FilesystemEntity

@Dao
interface FilesystemDao {
    @Query("select * from filesystems")
    fun getAllFilesystems: List<FilesystemEntity>

    @Query("select * from filesystems where name = :name")
    fun findFilesystemByName(name: String): FilesystemEntity

    @Query("select * from filesystems where filesystemId = :id")
    fun findFilesystemById(id: Long): FilesystemEntity

    @Insert(onConflict = REPLACE)
    fun insertFilesystem(filesystem: FilesystemEntity)

    @Update(onConflict = REPLACE)
    fun updateFileSystem(filesystem: FilesystemEntity)

    @Delete
    fun deleteFilesystem(filesystem: FilesystemEntity)
}