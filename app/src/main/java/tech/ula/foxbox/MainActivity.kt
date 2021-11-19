package tech.ula.foxbox

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import tech.ula.model.entities.App
import java.io.File
import java.net.URL

class MainActivity : AppCompatActivity() {

    override fun onNewIntent(intent: Intent?) {
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
        val ulaIntent = Intent(this, tech.ula.MainActivity::class.java)
        val app = App("firefox","browser", "debian", false, true, false, 1)
        ulaIntent.putExtra("app", app)
        if (intent.data != null) {
            val intentDir = File(filesDir, "Intents")
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