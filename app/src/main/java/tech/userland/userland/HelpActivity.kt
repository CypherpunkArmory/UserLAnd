package tech.userland.userland

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import kotlinx.android.synthetic.main.activity_help.*

class HelpActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)
        setSupportActionBar(toolbar)


        val helpButton: Button = findViewById(R.id.help_toc_button)
        helpButton.setOnClickListener { navigateToToc() }
    }

    fun navigateToToc(): Boolean {
        val intent = Intent(this, TermsAndConditionsActivity::class.java)
        startActivity(intent)
        return true
    }
}