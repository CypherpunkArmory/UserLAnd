package tech.ula.viewmodel

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import kotlinx.coroutines.* // ktlint-disable no-wildcard-imports
import tech.ula.model.repositories.UlaDatabase
import tech.ula.model.entities.Filesystem
import java.io.File
import kotlin.coroutines.CoroutineContext

class FilesystemEditViewModel(private val ulaDatabase: UlaDatabase) : ViewModel(), CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onCleared() {
        job.cancel()
        super.onCleared()
    }

    fun insertFilesystem(filesystem: Filesystem, coroutineScope: CoroutineScope = this) = coroutineScope.launch {
        withContext(Dispatchers.IO) {
            ulaDatabase.filesystemDao().insertFilesystem(filesystem)
        }
    }

    fun insertFilesystemFromBackup(
        filesystem: Filesystem,
        backupPath: String,
        filesDir: File,
        coroutineScope: CoroutineScope = this
    ) = coroutineScope.launch {
        withContext(Dispatchers.IO) {
            filesystem.isCreatedFromBackup = true
            val id = ulaDatabase.filesystemDao().insertFilesystem(filesystem)

            val filesystemSupportDir = File("${filesDir.absolutePath}/$id/support")
            filesystemSupportDir.mkdirs()

            val backup = File(backupPath)
            val backupTarget = File("${filesystemSupportDir.absolutePath}/rootfs.tar.gz")
            backup.copyTo(backupTarget, overwrite = true)
        }
    }

    fun updateFilesystem(filesystem: Filesystem, coroutineScope: CoroutineScope = this) = coroutineScope.launch {
        withContext(Dispatchers.IO) {
            ulaDatabase.filesystemDao().updateFilesystem(filesystem)
            ulaDatabase.sessionDao().updateFilesystemNamesForAllSessions()
        }
    }
}

class FilesystemEditViewmodelFactory(private val ulaDatabase: UlaDatabase) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return FilesystemEditViewModel(ulaDatabase) as T
    }
}