package tech.userland.userland.utils

import kotlinx.coroutines.experimental.*
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