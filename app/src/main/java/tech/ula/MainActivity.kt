package tech.ula

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupActionBarWithNavController
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)

        val navController: NavController = navHost.findNavController()
        setupActionBarWithNavController(navController, drawer_layout)

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        return super.onCreateOptionsMenu(menu)
    }
}