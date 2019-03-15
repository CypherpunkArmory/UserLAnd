package tech.ula.viewmodel

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.* // ktlint-disable no-wildcard-imports
import tech.ula.model.repositories.UlaDatabase
import tech.ula.model.entities.Filesystem
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import kotlin.coroutines.CoroutineContext

sealed class FilesystemImportStatus
object ImportSuccess : FilesystemImportStatus()
object UriUnselected : FilesystemImportStatus()
data class ImportFailure(val reason: String) : FilesystemImportStatus()

class FilesystemEditViewModel(private val ulaDatabase: UlaDatabase) : ViewModel(), CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onCleared() {
        job.cancel()
        super.onCleared()
    }

    private val importStatusLiveData = MutableLiveData<FilesystemImportStatus>()

    var backupUri: Uri? = null

    fun getImportStatusLiveData(): LiveData<FilesystemImportStatus> {
        return importStatusLiveData
    }

    fun insertFilesystem(filesystem: Filesystem, coroutineScope: CoroutineScope = this) = coroutineScope.launch {
        withContext(Dispatchers.IO) {
            ulaDatabase.filesystemDao().insertFilesystem(filesystem)
        }
    }

    fun insertFilesystemFromBackup(
        contentResolver: ContentResolver,
        filesystem: Filesystem,
        filesDir: File,
        coroutineScope: CoroutineScope = this
    ) = coroutineScope.launch {
        withContext(Dispatchers.IO) {
            if (backupUri == null) {
                importStatusLiveData.postValue(UriUnselected)
                return@withContext
            }

            filesystem.isCreatedFromBackup = true
            val id = ulaDatabase.filesystemDao().insertFilesystem(filesystem)

            try {
                val filesystemSupportDir = File("${filesDir.absolutePath}/$id/support")
                filesystemSupportDir.mkdirs()
                val destination = File("${filesystemSupportDir.absolutePath}/rootfs.tar.gz")

                val inputStream = contentResolver.openInputStream(backupUri!!)
                if (inputStream == null) {
                    ulaDatabase.filesystemDao().deleteFilesystemById(id)
                    importStatusLiveData.postValue(ImportFailure("Could not open input stream"))
                    return@withContext
                }

                val streamOutput = FileOutputStream(destination)
                inputStream.use { input ->
                    streamOutput.use { fileOut ->
                        input!!.copyTo(fileOut)
                    }
                }
            } catch (e: Exception) {
                ulaDatabase.filesystemDao().deleteFilesystemById(id)
                importStatusLiveData.postValue(ImportFailure(e.toString()))
            }

            backupUri = null
            importStatusLiveData.postValue(ImportSuccess)
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