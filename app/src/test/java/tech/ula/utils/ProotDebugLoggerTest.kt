package tech.ula.utils

import android.content.Context
import android.content.SharedPreferences
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class ProotDebugLoggerTest {

    @get:Rule val tempFolder = TemporaryFolder()

    @Mock lateinit var mockDefaultSharedPreferences: SharedPreferences

    private lateinit var prootDebugLogger: ProotDebugLogger

    @Before
    fun setup() {
        prootDebugLogger = ProotDebugLogger(mockDefaultSharedPreferences, tempFolder.root.path)
    }

    @Test
    fun `Property isEnabled fetches from cache`() {
        whenever(mockDefaultSharedPreferences.getBoolean("pref_proot_debug_enabled", false))
                .thenReturn(true)

        assertTrue(prootDebugLogger.isEnabled)
    }
}