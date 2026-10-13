package tech.ula.aicli

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import tech.ula.library.model.entities.App
import java.io.File

class MainActivity : AppCompatActivity() {

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent != null) {
            processIntent(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        processIntent(this.intent)
    }

    fun processIntent(intent: Intent) {
        val ulaIntent = Intent(this, tech.ula.library.MainActivity::class.java)
        val app = App("aicli","distribution", "aicli", true, false, "false",false, 1)
        ulaIntent.putExtra("app", app)
        if (intent.data != null) {
            val emulatedScopedDir = getExternalFilesDir(null)!!
            val intentDir = File(emulatedScopedDir, "Intents")
            intentDir.mkdirs()
            val urlFile = File(intentDir, ".url.txt")
            val finalUrlFile = File(intentDir, "url.txt")
            val uri: Uri? = intent.data
            val url = uri.toString()
            urlFile.writeText("$url")
            urlFile.renameTo(finalUrlFile)
        }
        this.startActivity(ulaIntent)
        finish()
    }

}