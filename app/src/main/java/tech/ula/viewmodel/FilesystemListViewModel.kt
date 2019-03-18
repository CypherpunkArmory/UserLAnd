package tech.ula.viewmodel

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import kotlinx.coroutines.* // ktlint-disable no-wildcard-imports
import tech.ula.R
import tech.ula.model.daos.FilesystemDao
import tech.ula.model.daos.SessionDao
import tech.ula.model.entities.Filesystem
import tech.ula.utils.FilesystemUtility
import java.io.File
import java.lang.Exception
import kotlin.coroutines.CoroutineContext
import tech.ula.model.entities.Session

sealed class FilesystemExportStatus
data class ExportUpdate(val details: String) : FilesystemExportStatus()
object ExportSuccess : FilesystemExportStatus()
data class ExportFailure(val reason: Int) : FilesystemExportStatus()
object ExportStarted : FilesystemExportStatus()

class FilesystemListViewModel(private val filesystemDao: FilesystemDao, private val sessionDao: SessionDao, private val filesystemUtility: FilesystemUtility) : ViewModel(), CoroutineScope {

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
    private val exportUpdateListener: (String) -> Unit = { details ->
        exportStatusLiveData.postValue(ExportUpdate(details))
    }

    fun getExportStatusLiveData(): LiveData<FilesystemExportStatus> {
        return exportStatusLiveData
    }

    private val activeSessions: LiveData<List<Session>> by lazy {
        sessionDao.findActiveSessions()
    }

    fun getAllFilesystems(): LiveData<List<Filesystem>> {
        return filesystems
    }

    fun getAllActiveSessions(): LiveData<List<Session>> {
        return activeSessions
    }

    fun deleteFilesystemById(id: Long, coroutineScope: CoroutineScope = this) = coroutineScope.launch {
        withContext(Dispatchers.IO) {
            filesystemDao.deleteFilesystemById(id)
        }
    }

    fun compressFilesystemAndExportToStorage(
        filesystem: Filesystem,
        filesDir: File,
        externalStorageDirectory: File,
        coroutineScope: CoroutineScope = this
    ) = coroutineScope.launch {
        withContext(Dispatchers.IO) {
            exportStatusLiveData.postValue(ExportStarted)
            val backupName = "${filesystem.name}-${filesystem.distributionType}-rootfs.tar.gz"
            val externalBackupFile = File("${externalStorageDirectory.path}/$backupName")
            val localTempBackupFile = File("${filesDir.path}/rootfs.tar.gz")
            if (!externalStorageDirectory.exists()) externalStorageDirectory.mkdirs()

            filesystemUtility.compressFilesystem(filesystem, localTempBackupFile, exportUpdateListener)

            if (!localTempBackupFile.exists()) {
                exportStatusLiveData.postValue(ExportFailure(R.string.error_export_to_local_failed))
                return@withContext
            }

            try {
                localTempBackupFile.copyTo(externalBackupFile)
                localTempBackupFile.delete()
            } catch (e: Exception) {
                exportStatusLiveData.postValue(ExportFailure(R.string.error_export_to_external_failed))
                localTempBackupFile.delete()
                return@withContext
            }

            when (externalBackupFile.exists() && externalBackupFile.length() > 0) {
                true -> exportStatusLiveData.postValue(ExportSuccess)
                false -> exportStatusLiveData.postValue(ExportFailure(R.string.error_export_to_external_failed_no_data))
            }
        }
    }

    fun startExport(filesystem: Filesystem, activeSessions: List<Session>, filesDir: File, externalDestination: File) {
        if (activeSessions.isEmpty()) {
            compressFilesystemAndExportToStorage(filesystem, filesDir, externalDestination)
        } else {
            exportStatusLiveData.postValue(ExportFailure(R.string.deactivate_sessions))
        }
    }
}

class FilesystemListViewmodelFactory(private val filesystemDao: FilesystemDao, private val sessionDao: SessionDao, private val filesystemUtility: FilesystemUtility) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return FilesystemListViewModel(filesystemDao, sessionDao, filesystemUtility) as T
    }
}