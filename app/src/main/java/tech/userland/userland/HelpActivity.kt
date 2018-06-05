package tech.userland.userland

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import kotlinx.android.synthetic.main.activity_help.*

class HelpActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)
        setSupportActionBar(toolbar)


        help_toc_button.setOnClickListener { navigateToToc() }
        github_logo.setOnClickListener {
            val intent = Intent("android.intent.action.VIEW", Uri.parse("https://github.com/CypherpunkArmory/UserLAnd/issues"))
            startActivity(intent)
        }
    }

    fun navigateToToc(): Boolean {
        val intent = Intent(this, TermsAndConditionsActivity::class.java)
        startActivity(intent)
        return true
    }
}