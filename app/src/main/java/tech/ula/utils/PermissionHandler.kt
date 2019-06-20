package tech.ula.utils

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import tech.ula.R

class PermissionHandler {
    companion object {
        private const val permissionRequestCode = 1234

        fun permissionsAreGranted(context: Context): Boolean {
            return (ContextCompat.checkSelfPermission(context,
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&

                    ContextCompat.checkSelfPermission(context,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
        }

        fun permissionsWereGranted(requestCode: Int, grantResults: IntArray): Boolean {
            return when (requestCode) {
                permissionRequestCode -> {
                    (grantResults.isNotEmpty() &&
                            grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                            grantResults[1] == PackageManager.PERMISSION_GRANTED)
                }
                else -> false
            }
        }

        @TargetApi(Build.VERSION_CODES.M)
        fun showPermissionsNecessaryDialog(activity: Activity) {
            val builder = AlertDialog.Builder(activity)
            builder.setMessage(R.string.alert_permissions_necessary_message)
                    .setTitle(R.string.alert_permissions_necessary_title)
                    .setPositiveButton(R.string.button_ok) { dialog, _ ->
                        activity.requestPermissions(arrayOf(
                                Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                                permissionRequestCode)
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.alert_permissions_necessary_cancel_button) { dialog, _ ->
                        dialog.dismiss()
                    }
            builder.create().show()
        }
    }
}