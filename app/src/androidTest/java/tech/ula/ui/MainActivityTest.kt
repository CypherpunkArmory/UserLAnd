package tech.ula.ui

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tech.ula.*
import tech.ula.androidTestHelpers.checkInvisible
import tech.ula.androidTestHelpers.checkVisible
import tech.ula.androidTestHelpers.waitForRefresh

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
        R.id.app_list_fragment.checkVisible()
        R.id.swipe_refresh.waitForRefresh<SwipeRefreshLayout>(activity)
        R.id.swipe_refresh.checkVisible()
    }
}