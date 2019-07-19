package tech.ula.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import com.schibsted.spain.barista.assertion.BaristaListAssertions.assertDisplayedAtPosition
import com.schibsted.spain.barista.assertion.BaristaVisibilityAssertions.assertContains
import com.schibsted.spain.barista.assertion.BaristaVisibilityAssertions.assertDisplayed
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
    val activityRule = ActivityTestRule(MainActivity::class.java)

    private lateinit var activity: MainActivity
    private val appName = "Alpine"
    private val username = "alpine_user"
    private val sshPassword = "ssh"
    private val vncPassword = "vncpass"
    private val homeLocation = "1/home/$username"
    private lateinit var homeDirectory: File

    @Before
    fun setup() {
        activity = activityRule.activity
        homeDirectory = File(activity.filesDir, homeLocation)
    }

    @Test
    fun testHappyPath() {
        Thread.sleep(1000)
        assertDisplayed(R.id.app_list_fragment)

        waitForSwipeRefresh(R.id.swipe_refresh, activity)

        // Click alpine
        assertDisplayedAtPosition(R.id.list_apps, 1, R.id.apps_name, appName)
        clickListItem(R.id.list_apps, 1)

        // Handle permissions
        assertContains(R.string.alert_permissions_necessary_title)
        clickDialogPositiveButton()
        allowPermissions()

        // Set filesystem credentials
        Thread.sleep(2000)
        assertContains(R.string.filesystem_credentials_reasoning)
        writeTo(R.id.text_input_username, username)
        writeTo(R.id.text_input_password, sshPassword)
        writeTo(R.id.text_input_vnc_password, vncPassword)
        clickDialogPositiveButton()

        // Set session type to ssh
        assertContains(R.string.prompt_app_connection_type_preference)
        clickRadioButtonItem(R.id.radio_apps_service_type_preference, R.id.ssh_radio_button)
        clickDialogPositiveButton()

        // Wait for progress dialog to complete
        assertDisplayed(R.id.progress_bar_session_list)
        waitForDisplay(R.string.progress_fetching_asset_lists)
        waitForDisplay(R.string.progress_downloading)
        waitForDisplay(R.string.progress_copying_downloads)
        waitForDisplay(R.string.progress_verifying_assets)
        waitForDisplay(R.string.progress_setting_up_filesystem)
        waitForDisplay(R.string.progress_starting)

        // Enter session and create a file to verify correct access
        waitForDisplay(R.id.terminal_view)
        sshPassword.enterText()
        Thread.sleep(500)
        "touch test.txt".enterText()
        Thread.sleep(500)
        val expectedFile = File(homeDirectory, "test.txt")
        assertTrue(expectedFile.exists())

        // Download and use a package, creating status files
        val expectedStatusFiles = doHappyPathTestScript()
        Thread.sleep(500)
        for (file in expectedStatusFiles) {
            waitForFile(file, timeout = 60_000)
        }
    }

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