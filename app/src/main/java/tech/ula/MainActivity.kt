package tech.ula

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Bundle
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.Toast
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.findNavController
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.NavigationUI.setupWithNavController
import kotlinx.android.synthetic.main.activity_main.*
import tech.ula.utils.NotificationUtility

interface OnFragmentDataPassed {
    fun onFragmentDataPassed(data: String)
}

class MainActivity : AppCompatActivity(), OnFragmentDataPassed {

    private val permissionRequestCode = 1000
    private var currentFragmentDisplaysProgressDialog = false

    private val navController: NavController by lazy {
        findNavController(R.id.nav_host_fragment)
    }

    private val notificationManager by lazy {
        NotificationUtility(this)
    }

    private val serverServiceBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val type = it.getStringExtra("type")
                when (type) {
                    "startProgressBar" -> startProgressBar()
                    "updateProgressBar" -> updateProgressBar(it)
                    "killProgressBar" -> killProgressBar()
                    "isProgressBarActive" -> syncProgressBarDisplayedWithService(it)
                    "networkUnavailable" -> displayNetworkUnavailableDialog()
                    "assetListFailure" -> displayAssetListFailureDialog()
                    "displayNetworkChoices" -> displayNetworkChoicesDialog()
                    "toast" -> showToast(it)
                    "dialog" -> showDialog(it)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        notificationManager.createServiceNotificationChannel() // Android O requirement
        navController.addOnNavigatedListener { _, destination ->
            currentFragmentDisplaysProgressDialog =
                    destination.label == "Sessions" ||
                    destination.label == "Applications"
            if (!currentFragmentDisplaysProgressDialog) killProgressBar()
        }

        setupWithNavController(bottom_nav_view, navController)
    }

    override fun onSupportNavigateUp() = navController.navigateUp()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_options, menu)
        return true
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(serverServiceBroadcastReceiver, IntentFilter(ServerService.SERVER_SERVICE_RESULT))
    }

    override fun onResume() {
        super.onResume()

        val intent = Intent(this, ServerService::class.java)
                .putExtra("type", "isProgressBarActive")
        this.startService(intent)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return NavigationUI.onNavDestinationSelected(item,
                Navigation.findNavController(this, R.id.nav_host_fragment)) ||
                super.onOptionsItemSelected(item)
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(this)
                .unregisterReceiver(serverServiceBroadcastReceiver)
    }

    override fun onFragmentDataPassed(data: String) {
        when (data) {
            "permissionsRequired" -> showPermissionsNecessaryDialog()
        }
    }

    private fun showToast(intent: Intent) {
        val content = intent.getIntExtra("id", -1)
        if (content == -1) return
        Toast.makeText(this, content, Toast.LENGTH_LONG).show()
    }

    private fun showPermissionsNecessaryDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage(R.string.alert_permissions_necessary_message)
                .setTitle(R.string.alert_permissions_necessary_title)
                .setPositiveButton(R.string.alert_permissions_necessary_ok_button) {
                    dialog, _ ->
                    requestPermissions(arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                            permissionRequestCode)
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.alert_permissions_necessary_cancel_button) {
                    dialog, _ ->
                    dialog.dismiss()
                }
        builder.create().show()
    }

    private fun showDialog(intent: Intent) {
        when (intent.getStringExtra("dialogType")) {
            "errorFetchingAssetLists" -> displayAssetListFailureDialog()
            "wifiRequired" -> displayNetworkChoicesDialog()
            "extractionFailed" -> displayExtractionFailedDialog()
            "filesystemIsMissingRequiredAssets" -> displayFilesystemMissingRequiredAssets()
        }
    }

    private fun displayNetworkUnavailableDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage(R.string.alert_network_unavailable_message)
                .setTitle(R.string.alert_network_unavailable_title)
                .setPositiveButton(R.string.alert_network_unavailable_cancel_button) {
                    dialog, _ ->
                    dialog.dismiss()
                }
                .create()
                .show()
    }

    private fun displayAssetListFailureDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage(R.string.alert_asset_list_failure_message)
                .setTitle(R.string.alert_asset_list_failure_title)
                .setPositiveButton(R.string.alert_asset_list_failure_positive_button) {
                    dialog, _ ->
                    dialog.dismiss()
                }
                .create()
                .show()
    }

    private fun displayExtractionFailedDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage(R.string.alert_extraction_failure_message)
                .setTitle(R.string.alert_extraction_failure_title)
                .setPositiveButton(R.string.alert_extraction_failure_positive_button) {
                    dialog, _ ->
                    dialog.dismiss()
                }
                .create()
                .show()
    }

    private fun displayFilesystemMissingRequiredAssets() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage(R.string.alert_filesystem_missing_requirements_message)
                .setTitle(R.string.alert_filesystem_missing_requirements_title)
                .setPositiveButton(R.string.alert_filesystem_missing_requirements_positive_button) {
                    dialog, _ ->
                    dialog.dismiss()
                }
                .create()
                .show()
    }

    private fun startProgressBar() {
        if (!currentFragmentDisplaysProgressDialog) return

        val inAnimation = AlphaAnimation(0f, 1f)
        inAnimation.duration = 200
        layout_progress.animation = inAnimation
        layout_progress.visibility = View.VISIBLE
        layout_progress.isFocusable = true
        layout_progress.isClickable = true
    }

    private fun killProgressBar() {
        val outAnimation = AlphaAnimation(1f, 0f)
        outAnimation.duration = 200
        layout_progress.animation = outAnimation
        layout_progress.visibility = View.GONE
        layout_progress.isFocusable = false
        layout_progress.isClickable = false
    }

    private fun updateProgressBar(intent: Intent) {
        if (!currentFragmentDisplaysProgressDialog) return

        layout_progress.visibility = View.VISIBLE
        layout_progress.isFocusable = true
        layout_progress.isClickable = true

        val step = intent.getStringExtra("step")
        val details = intent.getStringExtra("details")
        text_session_list_progress_step.text = step
        text_session_list_progress_details.text = details
    }

    private fun syncProgressBarDisplayedWithService(intent: Intent) {
        val isActive = intent.getBooleanExtra("isProgressBarActive", false)
        if (isActive) startProgressBar()
        else killProgressBar()
    }

    private fun displayNetworkChoicesDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage(R.string.alert_wifi_disabled_message)
                .setTitle(R.string.alert_wifi_disabled_title)
                .setPositiveButton(R.string.alert_wifi_disabled_continue_button) {
                    dialog, _ ->
                    dialog.dismiss()
                    val serviceIntent = Intent(this, ServerService::class.java)
                    serviceIntent.putExtra("type", "forceDownloads")
                    this.startService(serviceIntent)
                }
                .setNegativeButton(R.string.alert_wifi_disabled_turn_on_wifi_button) {
                    dialog, _ ->
                    dialog.dismiss()
                    startActivity(Intent(WifiManager.ACTION_PICK_WIFI_NETWORK))
                    killProgressBar()
                }
                .setNeutralButton(R.string.alert_wifi_disabled_cancel_button) {
                    dialog, _ ->
                    dialog.dismiss()
                    killProgressBar()
                }
                .setOnCancelListener {
                    killProgressBar()
                }
                .create()
                .show()
    }
}