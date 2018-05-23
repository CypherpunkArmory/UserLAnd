package tech.userland.userland

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_settings.*
import tech.userland.userland.ui.SettingsFragment

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fragmentManager.beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
                .commit()

    }
}