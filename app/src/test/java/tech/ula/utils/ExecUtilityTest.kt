package tech.ula.utils

import org.junit.Assert.*
import org.junit.Before
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class ExecUtilityTest {
    lateinit var execUtility: ExecUtility

    @Mock
    lateinit var fileUtility: FileUtility

    @Mock

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        execUtility
    }
}