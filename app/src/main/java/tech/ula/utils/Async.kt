package tech.ula.utils

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.android.UI

suspend fun <T> async(block: suspend CoroutineScope.() -> T): Deferred<T> {
    return async(CommonPool) { block() }
}

suspend fun <T> asyncAwait(block: suspend CoroutineScope.() -> T): T {
    return async(block).await()
}

fun launchAsync(block: suspend CoroutineScope.() -> Unit): Job {
    return launch(UI) { block() }
}