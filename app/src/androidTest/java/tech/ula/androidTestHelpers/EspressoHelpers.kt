package tech.ula.androidTestHelpers

import android.app.Activity
import android.view.View
import androidx.annotation.IdRes
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.test.espresso.Espresso
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers

internal fun @receiver:IdRes Int.matchView(): ViewInteraction =
        Espresso.onView(ViewMatchers.withId(this))

internal fun ViewInteraction.checkVisible() =
        check(ViewAssertions.matches(ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)))

internal fun ViewInteraction.checkInvisible() =
        check(ViewAssertions.matches(ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.INVISIBLE)))

internal fun @receiver:IdRes Int.checkVisible() =
        this.matchView().checkVisible()

internal fun @receiver:IdRes Int.checkInvisible() =
        this.matchView().checkInvisible()

internal fun <T: View> Int.waitForVisibilityChange(activity: Activity) {
    val view = activity.findViewById<T>(this)
    val target = if (view.visibility == View.VISIBLE) {
        View.INVISIBLE
    } else {
        View.VISIBLE
    }
    while (view.visibility != target) Thread.sleep(1)
}

internal fun <T: SwipeRefreshLayout> Int.waitForRefresh(activity: Activity) {
    val view = activity.findViewById<T>(this)
    while (view.isRefreshing) Thread.sleep(1)
}