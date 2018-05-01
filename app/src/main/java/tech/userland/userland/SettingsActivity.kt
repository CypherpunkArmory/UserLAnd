package tech.userland.userland

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_settings.*

class SettingsActivity : AppCompatActivity() {
    val settingsList: ArrayList<String> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        genStubSettingsList()

        settings_list.layoutManager = LinearLayoutManager(this)
        settings_list.adapter = ListAdapter(settingsList, this)

    }

    fun genStubSettingsList() {
        settingsList.add("test1")
        settingsList.add("test2")
    }
}