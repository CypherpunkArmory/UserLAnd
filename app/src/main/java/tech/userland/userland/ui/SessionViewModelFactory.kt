package tech.userland.userland.ui

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import tech.userland.userland.database.repositories.SessionRepository

class SessionViewModelFactory(private val sessionRepository: SessionRepository) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SessionViewModel::class.java)) {
            return SessionViewModel(sessionRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}