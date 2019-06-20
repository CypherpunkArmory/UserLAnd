package tech.ula.utils

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class DeviceDimensionsTest {

    @Mock
    lateinit var deviceDimensions: DeviceDimensions

    @Mock
    lateinit var windowManager: WindowManager

    @Mock
    lateinit var displayMetrics: DisplayMetrics

    @Mock
    lateinit var defaultDisplay: Display

    @Before
    fun setup() {
        deviceDimensions = DeviceDimensions()
        whenever(windowManager.defaultDisplay).thenReturn(defaultDisplay)
    }

    private fun setDimensions(displayMetrics: DisplayMetrics, width: Int, height: Int) {
        displayMetrics.widthPixels = width
        displayMetrics.heightPixels = height
    }

    @Test
    fun `Device dimensions that are taller in height will have the height value first`() {
        setDimensions(displayMetrics, width = 100, height = 200)
        deviceDimensions.getDeviceDimensions(windowManager, displayMetrics, Configuration.ORIENTATION_PORTRAIT)
        val geometry = deviceDimensions.getGeometry()
        assertEquals(geometry, "200x100")
    }

    @Test
    fun `Device dimensions that are longer in width will have the width value first`() {
        setDimensions(displayMetrics, width = 300, height = 200)
        deviceDimensions.getDeviceDimensions(windowManager, displayMetrics, Configuration.ORIENTATION_PORTRAIT)
        val geometry = deviceDimensions.getGeometry()
        assertEquals(geometry, "300x200")
    }
}