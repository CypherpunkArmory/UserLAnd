package tech.ula.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext

sealed class CloudState
sealed class LoginResult : CloudState() {
    object Success : LoginResult()
    object Failure : LoginResult()
}

class CloudDemoViewModel : ViewModel(), CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onCleared() {
        job.cancel()
        super.onCleared()
    }

    private val cloudState = MutableLiveData<CloudState>()

    fun getCloudState(): LiveData<CloudState> {
        return cloudState
    }

    fun handleLoginClick() {
        cloudState.postValue(LoginResult.Success)
    }

    fun handleConnectClick() {
        cloudState.postValue(LoginResult.Failure)
    }
}

class CloudDemoViewModelFactory : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return CloudDemoViewModel() as T
    }
}