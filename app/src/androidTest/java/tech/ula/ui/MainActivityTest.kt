package tech.ula.ui

import android.Manifest
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import com.schibsted.spain.barista.assertion.BaristaListAssertions.assertDisplayedAtPosition
import com.schibsted.spain.barista.assertion.BaristaVisibilityAssertions
import com.schibsted.spain.barista.assertion.BaristaVisibilityAssertions.assertContains
import com.schibsted.spain.barista.assertion.BaristaVisibilityAssertions.assertDisplayed
import com.schibsted.spain.barista.interaction.BaristaDialogInteractions.clickDialogPositiveButton
import com.schibsted.spain.barista.interaction.BaristaListInteractions.clickListItem
import com.schibsted.spain.barista.interaction.PermissionGranter
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tech.ula.MainActivity
import tech.ula.R
import tech.ula.androidTestHelpers.checkVisible
import tech.ula.androidTestHelpers.waitForRefresh
import tech.ula.androidTestHelpers.waitForSwipeRefresh

@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityTest {

    @get:Rule
    val activityRule = ActivityTestRule(MainActivity::class.java)

    private lateinit var activity: MainActivity

    @Before
    fun setup() {
        activity = activityRule.activity
    }

    @Test
    fun test() {
        assertDisplayed(R.id.app_list_fragment)
        waitForSwipeRefresh(R.id.swipe_refresh, activity)
        assertDisplayedAtPosition(R.id.list_apps, 1, R.id.apps_name, "Alpine")
        clickListItem(R.id.list_apps, 1)
        assertContains(R.string.alert_permissions_necessary_title)
        clickDialogPositiveButton()
        PermissionGranter.allowPermissionsIfNeeded(Manifest.permission.READ_EXTERNAL_STORAGE)
        PermissionGranter.allowPermissionsIfNeeded(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}