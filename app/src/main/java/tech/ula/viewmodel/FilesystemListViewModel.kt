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
import tech.ula.utils.ExecutionResult
import tech.ula.utils.FailedExecution

sealed class FilesystemListViewState

sealed class FilesystemExportState : FilesystemListViewState() {
    data class Update(val details: String) : FilesystemExportState()
    object Success : FilesystemExportState()
    data class Failure(val reason: Int, val details: String = "") : FilesystemExportState()
}

sealed class FilesystemDeleteState : FilesystemListViewState() {
    object InProgress : FilesystemDeleteState()
    object Success : FilesystemDeleteState()
    object Failure : FilesystemDeleteState()
}

class FilesystemListViewModel(private val filesystemDao: FilesystemDao, private val sessionDao: SessionDao, private val filesystemUtility: FilesystemUtility) : ViewModel(), CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onCleared() {
        job.cancel()
        super.onCleared()
    }

    private val viewState = MutableLiveData<FilesystemListViewState>()
    private val exportUpdateListener: (String) -> Unit = { details ->
        viewState.postValue(FilesystemExportState.Update(details))
    }

    private val unselectedFilesystem = Filesystem(id = -1, name = "UNSELECTED")
    private var filesystemToBackup = unselectedFilesystem

    fun setFilesystemToBackup(filesystem: Filesystem) {
        filesystemToBackup = filesystem
    }

    fun getViewState(): LiveData<FilesystemListViewState> {
        return viewState
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
            viewState.postValue(FilesystemDeleteState.InProgress)
//            activeSessions.value?.let { list ->
//                list.filter { it.filesystemId == id }.forEach { killSession(it) }
//            }

            try {
                filesystemUtility.deleteFilesystem(id)
            } catch (err: Exception) {
                viewState.postValue(FilesystemDeleteState.Failure)
                return@withContext
            }
            filesystemDao.deleteFilesystemById(id)
            viewState.postValue(FilesystemDeleteState.Success)
        }
    }

    fun getFilesystemBackupName(filesystem: Filesystem): String {
        return "${filesystem.name}-${filesystem.distributionType}-rootfs.tar.gz"
    }

    fun startExport(
        filesDir: File,
        publicExternalUri: Uri,
        contentResolver: ContentResolver,
        coroutineScope: CoroutineScope = this
    ) = coroutineScope.launch {
        when {
            activeSessions.value!!.isNotEmpty() -> {
                viewState.postValue(FilesystemExportState.Failure(R.string.deactivate_sessions))
                return@launch
            }
            filesystemToBackup == unselectedFilesystem -> {
                viewState.postValue(FilesystemExportState.Failure(R.string.error_export_filesystem_not_found))
                return@launch
            }
            else -> {
                compressFilesystemAndExportToStorage(filesDir, publicExternalUri, contentResolver)
            }
        }
    }

    private suspend fun compressFilesystemAndExportToStorage(
        filesDir: File,
        publicExternalUri: Uri,
        contentResolver: ContentResolver
    ) {
        withContext(Dispatchers.IO) {
            viewState.postValue(FilesystemExportState.Update("Starting export"))
            val tempBackupName = getFilesystemBackupName(filesystemToBackup)
            val localBackup = File(filesDir, tempBackupName)

            val result = filesystemUtility.compressFilesystem(filesystemToBackup, localBackup, exportUpdateListener)
            if (localBackupFailed(localBackup, result)) return@withContext

            try {
                localBackup.inputStream().use { inputStream ->
                    contentResolver.openOutputStream(publicExternalUri, "w")?.use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (err: Exception) {
                filesystemToBackup = unselectedFilesystem
                viewState.postValue(FilesystemExportState.Failure(R.string.error_export_copy_public_external_failure))
                return@withContext
            }

            filesystemToBackup = unselectedFilesystem
            viewState.postValue(FilesystemExportState.Success)
        }
    }

    private fun localBackupFailed(localBackup: File, result: ExecutionResult): Boolean {
        if (result is FailedExecution) {
            filesystemToBackup = unselectedFilesystem
            viewState.postValue(FilesystemExportState.Failure(R.string.error_export_execution_failure, result.reason))
            return true
        }

        if (!localBackup.exists() || localBackup.length() <= 0) {
            filesystemToBackup = unselectedFilesystem
            viewState.postValue(FilesystemExportState.Failure(R.string.error_export_local_failure))
            return true
        }

        return false
    }
}

class FilesystemListViewmodelFactory(private val filesystemDao: FilesystemDao, private val sessionDao: SessionDao, private val filesystemUtility: FilesystemUtility) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return FilesystemListViewModel(filesystemDao, sessionDao, filesystemUtility) as T
    }
}