package tech.userland.userland

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.ArrayAdapter
import kotlinx.android.synthetic.main.activity_settings.*

class SettingsActivity : AppCompatActivity() {
    val settingsList: ArrayList<String> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(toolbar)

        genStubSettingsList()

        list_settings.adapter = ArrayAdapter(this, R.layout.list_item_session, settingsList)

    }

    fun genStubSettingsList() {
        settingsList.add("test1")
        settingsList.add("test2")
    }
}