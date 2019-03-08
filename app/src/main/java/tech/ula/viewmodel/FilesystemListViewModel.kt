package tech.ula.viewmodel

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import kotlinx.coroutines.* // ktlint-disable no-wildcard-imports
import tech.ula.model.daos.FilesystemDao
import tech.ula.model.entities.Filesystem
import tech.ula.utils.FilesystemUtility
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import kotlin.coroutines.CoroutineContext

sealed class FilesystemExportStatus
object ExportSuccess : FilesystemExportStatus()
data class ExportFailure(val reason: String) : FilesystemExportStatus()

class FilesystemListViewModel(private val filesystemDao: FilesystemDao, private val filesystemUtility: FilesystemUtility) : ViewModel(), CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onCleared() {
        job.cancel()
        super.onCleared()
    }

    private val filesystems: LiveData<List<Filesystem>> by lazy {
        filesystemDao.getAllFilesystems()
    }

    private val exportStatusLiveData = MutableLiveData<FilesystemExportStatus>()

    fun getExportStatusLiveData(): LiveData<FilesystemExportStatus> {
        return exportStatusLiveData
    }

    fun getAllFilesystems(): LiveData<List<Filesystem>> {
        return filesystems
    }

    fun deleteFilesystemById(id: Long, coroutineScope: CoroutineScope = this) = coroutineScope.launch {
        withContext(Dispatchers.IO) {
            filesystemDao.deleteFilesystemById(id)
        }
    }

    fun compressFilesystem(
        filesystem: Filesystem,
        localTempDirectory: File,
        externalStorageDirectory: File,
        listener: (String) -> Any,
        coroutineScope: CoroutineScope = this
    ) = coroutineScope.launch {

        val backupName = "${filesystem.name}-${filesystem.distributionType}-rootfs.tar.gz"
        val externalDestinationUri = File("${externalStorageDirectory.path}/$backupName")
        if (!externalStorageDirectory.exists()) externalStorageDirectory.mkdirs()

        filesystemUtility.compressFilesystem(filesystem, File(localTempDirectory.path), listener)

        if (!File(localTempDirectory.path).exists()) {
            return@launch
        }

        val inputStream = File("$localTempDirectory/rootfs.tar.gz").inputStream()

        try {
            val streamOutput = FileOutputStream(externalDestinationUri)
            inputStream.use { input ->
                streamOutput.use { fileOut ->
                    input.copyTo(fileOut)
                }
            }
        } catch (e: Exception) {
            exportStatusLiveData.postValue(ExportFailure(e.toString()))
        }
        exportStatusLiveData.postValue(ExportSuccess)
    }
}

class FilesystemListViewmodelFactory(private val filesystemDao: FilesystemDao, private val filesystemUtility: FilesystemUtility) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return FilesystemListViewModel(filesystemDao, filesystemUtility) as T
    }
}