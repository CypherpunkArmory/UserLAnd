package tech.ula.utils

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.view.View
import androidx.annotation.IdRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import java.io.File

fun <A, B> zipLiveData(a: LiveData<A>, b: LiveData<B>): LiveData<Pair<A, B>> {
    return MediatorLiveData<Pair<A, B>>().apply {
        var lastA: A? = null
        var lastB: B? = null

        fun update() {
            val localLastA = lastA
            val localLastB = lastB
            if (localLastA != null && localLastB != null)
                this.value = Pair(localLastA, localLastB)
        }

        addSource(a) {
            lastA = it
            update()
        }
        addSource(b) {
            lastB = it
            update()
        }
    }
}

inline val Context.scopedStorageRoot: File
    get() = this.getExternalFilesDir(null) ?: run {
        val scopedStorageRoot = File(this.filesDir, "storage")
        scopedStorageRoot.mkdirs()
        scopedStorageRoot
    }

inline val Context.defaultSharedPreferences: SharedPreferences
    get() = this.getSharedPreferences("${this.packageName}_preferences", Context.MODE_PRIVATE)

inline fun <reified T : View> View.find(@IdRes id: Int): T = findViewById(id)
inline fun <reified T : View> Dialog.find(@IdRes id: Int): T = findViewById(id)