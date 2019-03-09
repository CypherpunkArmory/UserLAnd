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

    fun compressFilesystemAndExportToStorage(
        filesystem: Filesystem,
        localTempDirectory: File,
        externalStorageDirectory: File,
        listener: (String) -> Any,
        coroutineScope: CoroutineScope = this
    ) = coroutineScope.launch {

        val backupName = "${filesystem.name}-${filesystem.distributionType}-rootfs.tar.gz"
        val externalBackupFile = File("${externalStorageDirectory.path}/$backupName")
        val localTempBackupFile = File("${localTempDirectory.path}/rootfs.tar.gz")
        if (!externalStorageDirectory.exists()) externalStorageDirectory.mkdirs()

        filesystemUtility.compressFilesystem(filesystem, File(localTempDirectory.path), listener)

        if (!localTempBackupFile.exists()) {
            exportStatusLiveData.postValue(ExportFailure("Exporting to local directory failed"))
            return@launch
        }

        val inputStream = localTempBackupFile.inputStream()

        try {
            val streamOutput = FileOutputStream(externalBackupFile)
            inputStream.use { input ->
                streamOutput.use { fileOut ->
                    input.copyTo(fileOut)
                }
            }
        } catch (e: Exception) {
            exportStatusLiveData.postValue(ExportFailure(e.toString()))
        }

        when (externalBackupFile.exists()) {
            true -> exportStatusLiveData.postValue(ExportSuccess)
            false -> exportStatusLiveData.postValue(ExportFailure("Exporting to external directory failed"))
        }
    }
}

class FilesystemListViewmodelFactory(private val filesystemDao: FilesystemDao, private val filesystemUtility: FilesystemUtility) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return FilesystemListViewModel(filesystemDao, filesystemUtility) as T
    }
}