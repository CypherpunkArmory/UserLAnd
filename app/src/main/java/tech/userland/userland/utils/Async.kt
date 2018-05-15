package tech.userland.userland.utils

import kotlinx.coroutines.experimental.CoroutineScope
import kotlin.coroutines.experimental.CoroutineContext

class Async() {
    public fun launchAsync(UI: CoroutineContext block: suspend CoroutineScope.() -> Unit) {

    }
}