package tech.ula.gimp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import tech.ula.library.MainActivity
import tech.ula.library.model.entities.App
import java.io.File

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
        val ulaIntent = Intent(this, MainActivity::class.java)
        val app = App("gimp","Distribution", "gimp", false, true, false, 1)
        ulaIntent.putExtra("app", app)
        this.startActivity(ulaIntent)
        finish()
    }

}