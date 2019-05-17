package tech.ula.viewmodel

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
import tech.ula.utils.FailedExecution

sealed class FilesystemExportStatus
data class ExportUpdate(val details: String) : FilesystemExportStatus()
data class ExportSuccess(val backupName: String) : FilesystemExportStatus()
data class ExportFailure(val reason: Int, val details: String = "") : FilesystemExportStatus()
object ExportStarted : FilesystemExportStatus()

class FilesystemListViewModel(private val filesystemDao: FilesystemDao, private val sessionDao: SessionDao, private val filesystemUtility: FilesystemUtility) : ViewModel(), CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onCleared() {
        job.cancel()
        super.onCleared()
    }

    private val exportStatusLiveData = MutableLiveData<FilesystemExportStatus>()
    private val exportUpdateListener: (String) -> Unit = { details ->
        exportStatusLiveData.postValue(ExportUpdate(details))
    }

    private var currentBackupName = ""

    fun getExportStatusLiveData(): LiveData<FilesystemExportStatus> {
        return exportStatusLiveData
    }

    private val filesystems: LiveData<List<Filesystem>> by lazy {
        filesystemDao.getAllFilesystems()
    }

    fun getAllFilesystems(): LiveData<List<Filesystem>> {
        return filesystems
    }

    private val activeSessions: LiveData<List<Session>> by lazy {
        sessionDao.findActiveSessions()
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
        scopedExternalRoot: File,
        coroutineScope: CoroutineScope = this
    ) = coroutineScope.launch {
        withContext(Dispatchers.IO) {
            exportStatusLiveData.postValue(ExportStarted)
            currentBackupName = "${filesystem.name}-${filesystem.distributionType}-rootfs.tar.gz"
            val localBackup = File(filesDir, currentBackupName)
            val scopedBackup = File(scopedExternalRoot, currentBackupName)
            if (!scopedExternalRoot.exists()) scopedExternalRoot.mkdirs()

            val result = filesystemUtility.compressFilesystem(filesystem, localBackup, exportUpdateListener)
            if (result is FailedExecution) {
                exportStatusLiveData.postValue(ExportFailure(R.string.error_export_execution_failure, result.reason))
                return@withContext
            }

            if (!localBackup.exists()) {
                exportStatusLiveData.postValue(ExportFailure(R.string.error_export_local_failure))
                return@withContext
            }

            try {
                localBackup.copyTo(scopedBackup, overwrite = true)
                localBackup.delete()
            } catch (err: Exception) {
                localBackup.delete()
                exportStatusLiveData.postValue(ExportFailure(R.string.error_export_scoped_failure))
                return@withContext
            }

            when (scopedBackup.exists() && scopedBackup.length() > 0) {
                true -> exportStatusLiveData.postValue(ExportSuccess(currentBackupName))
                false -> exportStatusLiveData.postValue(ExportFailure(R.string.error_export_scoped_failure_no_data))
            }
        }
    }

    fun startExport(filesystem: Filesystem, filesDir: File, scopedExternalRoot: File) {
        if (activeSessions.value!!.isEmpty()) {
            compressFilesystemAndExportToStorage(filesystem, filesDir, scopedExternalRoot)
        } else {
            exportStatusLiveData.postValue(ExportFailure(R.string.deactivate_sessions))
        }
    }

    fun copyExportToExternal(
            scopedExternalRootPath: File,
            uri: Uri,
            contentResolver: ContentResolver,
            coroutineScope: CoroutineScope = this)
            = coroutineScope.launch {
        withContext(Dispatchers.IO) {
            // TODO we should probably create a backups folder at least
            if (currentBackupName == "") {
                exportStatusLiveData.postValue(ExportFailure(R.string.error_export_name_not_found))
                return@withContext
            }
            val backupFile = File(scopedExternalRootPath, currentBackupName)
            try {
                backupFile.inputStream().use { inputStream ->
                    contentResolver.openOutputStream(uri, "w")?.use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                return@withContext
            } catch (err: Exception) {
                exportStatusLiveData.postValue(ExportFailure(R.string.error_export_copy_public_external_failure))
                return@withContext
            }
        }
    }
}

class FilesystemListViewmodelFactory(private val filesystemDao: FilesystemDao, private val sessionDao: SessionDao, private val filesystemUtility: FilesystemUtility) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return FilesystemListViewModel(filesystemDao, sessionDao, filesystemUtility) as T
    }
}