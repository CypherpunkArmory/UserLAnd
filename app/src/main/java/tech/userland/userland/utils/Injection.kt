package tech.userland.userland.utils

import android.content.Context
import tech.userland.userland.ui.SessionViewModelFactory

fun provideSessionRepository(context: Context): SessionRepository {
    return SessionRepository(context)
}

fun provideSessionViewModelFactory(context: Context): SessionViewModelFactory {
    val sessionRepository = provideSessionRepository(context)
    return SessionViewModelFactory(sessionRepository)
}