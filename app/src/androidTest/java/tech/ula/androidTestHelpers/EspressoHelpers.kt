package tech.ula.androidTestHelpers

import android.app.Activity
import android.view.View
import androidx.annotation.IdRes
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.test.espresso.Espresso
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers

fun @receiver:IdRes Int.matchView(): ViewInteraction =
        Espresso.onView(ViewMatchers.withId(this))


fun ViewInteraction.checkVisible(): ViewInteraction =
        check(ViewAssertions.matches(ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)))

fun ViewInteraction.checkInvisible(): ViewInteraction =
        check(ViewAssertions.matches(ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.INVISIBLE)))

fun @receiver:IdRes Int.checkVisible(): ViewInteraction =
        this.matchView().checkVisible()

fun @receiver:IdRes Int.checkInvisible(): ViewInteraction =
        this.matchView().checkInvisible()

fun <T: View> Int.waitForVisibilityChange(activity: Activity) {
    val view = activity.findViewById<T>(this)
    val target = if (view.visibility == View.VISIBLE) {
        View.INVISIBLE
    } else {
        View.VISIBLE
    }
    while (view.visibility != target) Thread.sleep(1)
}

fun Int.waitForRefresh(activity: Activity) {
    val view = activity.findViewById<SwipeRefreshLayout>(this)
    while (view.isRefreshing) Thread.sleep(1)
}

fun waitForSwipeRefresh(@IdRes id: Int, activity: Activity) = id.waitForRefresh(activity)