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
object ExportSuccess : FilesystemExportStatus()
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

    private val unselectedFilesystem = Filesystem(id = -1, name = "UNSELECTED")
    private var filesystemToBackup = unselectedFilesystem

    fun setFilesystemToBackup(filesystem: Filesystem) {
        filesystemToBackup = filesystem
    }

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

    fun getFilesystemBackupName(filesystem: Filesystem): String {
        return "${filesystem.name}-${filesystem.distributionType}-rootfs.tar.gz"
    }

    fun compressFilesystemAndExportToStorage(
            filesDir: File,
            publicExternalUri: Uri,
            contentResolver: ContentResolver,
            coroutineScope: CoroutineScope = this
    ) = coroutineScope.launch {
        withContext(Dispatchers.IO) {
            exportStatusLiveData.postValue(ExportStarted)
            val tempBackupName = getFilesystemBackupName(filesystemToBackup)
            val localBackup = File(filesDir, tempBackupName)

            val result = filesystemUtility.compressFilesystem(filesystemToBackup, localBackup, exportUpdateListener)
            if (result is FailedExecution) {
                exportStatusLiveData.postValue(ExportFailure(R.string.error_export_execution_failure, result.reason))
                return@withContext
            }

            if (!localBackup.exists() || localBackup.length() <= 0) {
                exportStatusLiveData.postValue(ExportFailure(R.string.error_export_local_failure))
                return@withContext
            }

            val backupFile = File(filesDir, tempBackupName)
            try {
                backupFile.inputStream().use { inputStream ->
                    contentResolver.openOutputStream(publicExternalUri, "w")?.use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (err: Exception) {
                exportStatusLiveData.postValue(ExportFailure(R.string.error_export_copy_public_external_failure))
                return@withContext
            }

            exportStatusLiveData.postValue(ExportSuccess)
        }
    }

    fun startExport(filesDir: File, publicExternalUri: Uri, contentResolver: ContentResolver) {
        when {
            activeSessions.value!!.isNotEmpty() -> {
                exportStatusLiveData.postValue(ExportFailure(R.string.deactivate_sessions))
                return
            }
            filesystemToBackup == unselectedFilesystem -> {
                exportStatusLiveData.postValue(ExportFailure(R.string.error_export_filesystem_not_found))
                return
            }
            else -> {
                compressFilesystemAndExportToStorage(filesDir, publicExternalUri, contentResolver)
            }
        }
    }
}

class FilesystemListViewmodelFactory(private val filesystemDao: FilesystemDao, private val sessionDao: SessionDao, private val filesystemUtility: FilesystemUtility) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return FilesystemListViewModel(filesystemDao, sessionDao, filesystemUtility) as T
    }
}