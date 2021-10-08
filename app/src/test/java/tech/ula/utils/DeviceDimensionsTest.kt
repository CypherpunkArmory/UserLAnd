package tech.ula.utils

import android.content.SharedPreferences
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.BuildConfig

@RunWith(MockitoJUnitRunner::class)
class DeviceDimensionsTest {

    @Mock
    lateinit var mockDeviceDimensions: DeviceDimensions

    @Mock
    lateinit var mockWindowManager: WindowManager

    @Mock
    lateinit var mockDisplayMetrics: DisplayMetrics

    @Mock
    lateinit var mockDisplay: Display

    @Mock
    lateinit var mockSharedPreferences: SharedPreferences

    @Before
    fun setup() {
        mockDeviceDimensions = DeviceDimensions()
        whenever(mockWindowManager.defaultDisplay).thenReturn(mockDisplay)
    }

    private fun setDimensions(displayMetrics: DisplayMetrics, width: Int, height: Int) {
        displayMetrics.widthPixels = width
        displayMetrics.heightPixels = height
    }

    @Test
    fun `Device dimensions that are taller in height will have the height value first`() {
        setDimensions(mockDisplayMetrics, width = 100, height = 200)
        mockDeviceDimensions.saveDeviceDimensions(mockWindowManager, mockDisplayMetrics, Configuration.ORIENTATION_PORTRAIT, mockSharedPreferences)
        val geometry = mockDeviceDimensions.getScreenResolution()
        if (BuildConfig.FORCE_PORTRAIT_GEOMETRY) {
            assertEquals(geometry, "200x100")
        } else {
            assertEquals(geometry, "100x200")
        }
    }

    @Test
    fun `Device dimensions that are longer in width will have the width value first`() {
        setDimensions(mockDisplayMetrics, width = 300, height = 200)
        mockDeviceDimensions.saveDeviceDimensions(mockWindowManager, mockDisplayMetrics, Configuration.ORIENTATION_PORTRAIT, mockSharedPreferences)
        val geometry = mockDeviceDimensions.getScreenResolution()
        assertEquals(geometry, "300x200")
    }
}