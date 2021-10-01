package tech.ula.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.test.espresso.Espresso
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.* // ktlint-disable no-wildcard-imports
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import com.schibsted.spain.barista.assertion.BaristaListAssertions.assertDisplayedAtPosition
import com.schibsted.spain.barista.assertion.BaristaVisibilityAssertions.assertNotDisplayed
import com.schibsted.spain.barista.interaction.BaristaDialogInteractions.clickDialogPositiveButton
import com.schibsted.spain.barista.interaction.BaristaEditTextInteractions.writeTo
import com.schibsted.spain.barista.interaction.BaristaListInteractions.clickListItem
import com.schibsted.spain.barista.interaction.BaristaRadioButtonInteractions.clickRadioButtonItem
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tech.ula.MainActivity
import tech.ula.R
import tech.ula.androidTestHelpers.* // ktlint-disable no-wildcard-imports
import java.io.File

@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityTest {

    @get:Rule
    val intentTestRule = IntentsTestRule(MainActivity::class.java)

    // Permissions are granted automatically by firebase, so to keep parity we skip that step
    // locally as well.
    @get:Rule
    val grantPermission: GrantPermissionRule = GrantPermissionRule.grant(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    private lateinit var activity: MainActivity
    private val appName = "Alpine"
    private val username = "alpine_user"
    private val sshPassword = "ssh"
    private val vncPassword = "vncpass"
    private val homeLocation = "1/home/$username"
    private lateinit var homeDirectory: File

    @Before
    fun setup() {
        activity = intentTestRule.activity
        homeDirectory = File(activity.filesDir, homeLocation)
    }

    @Test
    fun test_ssh_session_can_be_started() {
        R.id.app_list_fragment.shortWaitForDisplay()

        R.id.swipe_refresh.waitForRefresh(activity)

        // Click alpine
        assertDisplayedAtPosition(R.id.list_apps, 0, R.id.apps_name, appName)
        clickListItem(R.id.list_apps, 0)

        // Set filesystem credentials
        R.string.filesystem_credentials_reasoning.waitForDisplay()
        writeTo(R.id.text_input_username, username)
        writeTo(R.id.text_input_password, sshPassword)
        writeTo(R.id.text_input_vnc_password, vncPassword)
        clickDialogPositiveButton()

        // Set session type to ssh
        R.string.prompt_app_connection_type_preference.shortWaitForDisplay()
        clickRadioButtonItem(R.id.radio_apps_service_type_preference, R.id.ssh_radio_button)
        clickDialogPositiveButton()

        // Wait for progress dialog to complete
        R.id.progress_bar_session_list.shortWaitForDisplay()
        R.string.progress_downloading.longWaitForDisplay()
        R.string.progress_copying_downloads.extraLongWaitForDisplay()
        R.string.progress_verifying_assets.waitForDisplay()
        R.string.progress_setting_up_filesystem.waitForDisplay()
        R.string.progress_starting.longWaitForDisplay()

        // Enter session and create a file to verify correct access
        R.id.terminal_view.longWaitForDisplay()
        sshPassword.enterAsNativeViewText()
        "touch test.txt".enterAsNativeViewText()
        val expectedFile = File(homeDirectory, "test.txt")
        assertTrue(expectedFile.exists())

        // Download and use a package, creating status files
        val expectedStatusFiles = doHappyPathTestScript()
        Thread.sleep(500)
        for (file in expectedStatusFiles) {
            waitForFile(file, timeout = 60_000)
        }

        // Return to apps list
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        R.id.app_list_fragment.shortWaitForDisplay()

        // Try to start a second session
        clickListItem(R.id.list_apps, 1)
        // Set up second session
        R.string.filesystem_credentials_reasoning.waitForDisplay()
        writeTo(R.id.text_input_username, username)
        writeTo(R.id.text_input_password, sshPassword)
        writeTo(R.id.text_input_vnc_password, vncPassword)
        clickDialogPositiveButton()
        R.string.prompt_app_connection_type_preference.shortWaitForDisplay()
        clickRadioButtonItem(R.id.radio_apps_service_type_preference, R.id.ssh_radio_button)
        clickDialogPositiveButton()
        // Asset second session cannot be started
        assertNotDisplayed(R.id.progress_bar_session_list)
        R.string.single_session_supported.displayedInToast()

        // Assert session can be restarted
        clickListItem(R.id.list_apps, 0)
        assertNotDisplayed(R.id.layout_progress)
        R.id.terminal_view.shortWaitForDisplay()
    }

    /*
    @Test
    fun test_vnc_session_can_be_started() {
        R.id.app_list_fragment.shortWaitForDisplay()

        R.id.swipe_refresh.waitForRefresh(activity)

        // Click alpine
        assertDisplayedAtPosition(R.id.list_apps, 0, R.id.apps_name, appName)
        clickListItem(R.id.list_apps, 0)

        // Set filesystem credentials
        R.string.filesystem_credentials_reasoning.waitForDisplay()
        writeTo(R.id.text_input_username, username)
        writeTo(R.id.text_input_password, sshPassword)
        writeTo(R.id.text_input_vnc_password, vncPassword)
        clickDialogPositiveButton()

        // Set session type to vnc
        R.string.prompt_app_connection_type_preference.shortWaitForDisplay()
        clickRadioButtonItem(R.id.radio_apps_service_type_preference, R.id.vnc_radio_button)
        clickDialogPositiveButton()

        // Wait for progress dialog to complete
        R.id.progress_bar_session_list.shortWaitForDisplay()
        R.string.progress_downloading.longWaitForDisplay()
        R.string.progress_copying_downloads.extraLongWaitForDisplay()
        R.string.progress_verifying_assets.waitForDisplay()
        R.string.progress_setting_up_filesystem.waitForDisplay()
        R.string.progress_starting.longWaitForDisplay()
        Thread.sleep(10000)

        val clientIntent = Intent()
        clientIntent.action = Intent.ACTION_VIEW
        clientIntent.type = "application/vnd.vnc"
        clientIntent.data = Uri.parse("vnc://127.0.0.1:5951/?VncUsername=$username&VncPassword=$vncPassword")
        clientIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        val packageManager = activity.packageManager
        val activities = packageManager.queryIntentActivities(clientIntent, 0)
        if (activities.size > 0) {
            // Match case where bVNC is present, e.g. test is running on personal device
            intended(hasAction(Intent.ACTION_VIEW))
            intended(hasData(Uri.parse("vnc://127.0.0.1:5951/?VncUsername=$username&VncPassword=$vncPassword")))
            intended(hasFlag(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            // Match case where bVNC is not present on device, e.g. firebase
            val packageName = "com.iiordanov.freebVNC"
            intended(hasAction(Intent.ACTION_VIEW))
            intended(hasData(Uri.parse("market://details?id=$packageName")))
            intended(hasFlag(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    */

    private fun doHappyPathTestScript(): List<File> {
        val startedFile = File(homeDirectory, "test.scriptStarted")
        val updatedFile = File(homeDirectory, "test.apkUpdated")
        val addedFile = File(homeDirectory, "test.pkgAdded")
        val indexFile = File(homeDirectory, "test.index")
        val finishedFile = File(homeDirectory, "test.finished")
        val script =
                """
            touch ${startedFile.name}
            sudo apk update
            touch ${updatedFile.name}
            sudo apk add curl
            touch ${addedFile.name}
            curl google.com > ${indexFile.name}
            touch ${finishedFile.name}
            """.trimIndent()

        executeScript(script, homeDirectory)
        return listOf(startedFile, updatedFile, addedFile, indexFile, finishedFile)
    }
}