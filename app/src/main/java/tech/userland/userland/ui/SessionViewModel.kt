package tech.userland.userland.ui

import android.arch.lifecycle.*
import tech.userland.userland.database.models.Session

class SessionViewModel(private val sessionRepository: SessionRepository) : ViewModel() {
//    lateinit var sessionRepository: SessionRepository
    val sessions = MutableLiveData<ArrayList<Session>>()

    fun getSessions(): LiveData<ArrayList<Session>> {
        sessions.value = sessionRepository.getAllSessions()
        return sessions
    }

    fun insertSession(session: Session) {
        sessionRepository.insertSession(session)
        getSessions()
    }

//    fun setRepository(sessionRepository: SessionRepository) {
//        this.sessionRepository = sessionRepository
//    }

//    companion object {
//        fun setRepository(sessionRepository: SessionRepository): SessionRepository {
//            return sessionRepository
//        }
//    }

//    companion object : ViewModelProvider.Factory {
//        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
//            if(modelClass.isAssignableFrom(SessionViewModel::class.java)) {
//                return SessionViewModel(sessionRepository) as T
//            }
//        }
//    }
//    companion object {
//        fun create(activity: FragmentActivity) : SessionViewModel {
//            return ViewModelProviders.of(activity).get(SessionViewModel::class.java)
//        }
//    }
}