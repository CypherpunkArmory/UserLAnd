package tech.ula.utils

import android.content.res.Configuration
import android.graphics.Point
import android.util.DisplayMetrics
import android.view.WindowManager

class DeviceDimensions {
    private var height = 720
    private var width = 1480

    fun saveDeviceDimensions(windowManager: WindowManager, displayMetrics: DisplayMetrics, orientation: Int) {
        val navBarSize = getNavigationBarSize(windowManager)
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        height = displayMetrics.heightPixels
        width = displayMetrics.widthPixels
        windowManager.defaultDisplay.getMetrics(displayMetrics)

        when (orientation) {
            Configuration.ORIENTATION_PORTRAIT -> if (navBarSize.y > 0) height += navBarSize.y
            Configuration.ORIENTATION_LANDSCAPE -> if (navBarSize.x > 0) width += navBarSize.x
            else -> return
        }
    }

    fun getScreenResolution(): String {
        return when (height > width) {
            true -> "${height}x$width"
            false -> "${width}x$height"
        }
    }

    private fun getNavigationBarSize(windowManager: WindowManager): Point {
        val display = windowManager.defaultDisplay
        val appSize = Point()
        val screenSize = Point()
        display.getSize(appSize)
        display.getRealSize(screenSize)

        return Point(screenSize.x - appSize.x, screenSize.y - appSize.y)
    }
}