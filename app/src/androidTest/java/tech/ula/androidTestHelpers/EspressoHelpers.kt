package tech.ula.androidTestHelpers

import android.app.Activity
import android.view.WindowManager
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.test.espresso.Espresso
import androidx.test.espresso.Root
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import com.schibsted.spain.barista.assertion.BaristaVisibilityAssertions.assertDisplayed
import com.schibsted.spain.barista.internal.failurehandler.BaristaException
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.UiDevice
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import android.view.KeyCharacterMap
import android.view.KeyEvent
import java.io.File
import java.util.concurrent.TimeoutException

fun waitForDisplay(@IdRes id: Int, timeout: Long = 300_000) {
    val test = {
        try {
            assertDisplayed(id)
            true
        } catch (err: BaristaException) {
            false
        }
    }
    waitForSuccess(test, timeout)
}

fun waitForFile(file: File, timeout: Long = 300_000) {
    val test = { file.exists() }
    waitForSuccess(test, timeout)
}

fun waitForSuccess(test: () -> Boolean, timeout: Long) {
    val startTime = System.currentTimeMillis()
    while (!test()) {
        val currTime = System.currentTimeMillis()
        if (currTime >= startTime + timeout) throw TimeoutException()
        Thread.sleep(10)
    }
}

fun Int.waitForRefresh(activity: Activity) {
    val view = activity.findViewById<SwipeRefreshLayout>(this)
    while (view.isRefreshing) Thread.sleep(1)
}

fun waitForSwipeRefresh(@IdRes id: Int, activity: Activity) = id.waitForRefresh(activity)

class ToastMatcher : TypeSafeMatcher<Root>() {
    override fun describeTo(description: Description) {
        description.appendText("is toast")
    }

    override fun matchesSafely(root: Root): Boolean {
        val type = root.windowLayoutParams.get().type
        @Suppress("DEPRECATION") // TYPE_TOAST is deprecated
        if (type == WindowManager.LayoutParams.TYPE_TOAST || type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) {
            val windowToken = root.decorView.windowToken
            val appToken = root.decorView.applicationWindowToken
            if (windowToken == appToken) {
                // Window isn't contained by any other windows
                return true
            }
        }
        return false
    }
}

fun @receiver:StringRes Int.matchText(): ViewInteraction =
        Espresso.onView(ViewMatchers.withText(this))

// TODO this doesn't quite work. will be useful for failure tests
fun @receiver:StringRes Int.notDisplayedInToast(): ViewInteraction =
        this.matchText().inRoot(ToastMatcher()).check(ViewAssertions.doesNotExist())

fun allowPermissions() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Thread.sleep(3000)
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val allowPermissions = device.findObject(UiSelector()
                .clickable(true)
                .checkable(false)
                .text("Allow"))
        if (allowPermissions.exists()) {
            allowPermissions.click()
        }
    }
}

fun String.enterText() {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val characterMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
    val events = characterMap.getEvents(this.toCharArray())
    for (event in events) {
        // There are ACTION_UP and ACTION_DOWN events generated. Only need to press keycode once
        if (event.action == KeyEvent.ACTION_UP) continue
        device.pressKeyCode(event.keyCode)
    }
    device.pressEnter()
}

fun executeScript(script: String, location: File) {
    val scriptName = "script.sh"
    val scriptFile = File(location, scriptName)
    scriptFile.writeText(script)
    "chmod 777 $scriptName".enterText()
    Thread.sleep(500)
    "bash $scriptName".enterText()
}