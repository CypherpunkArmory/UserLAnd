package tech.ula.utils

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.view.ViewGroup
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.* // ktlint-disable no-wildcard-imports
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.R

@RunWith(MockitoJUnitRunner::class)
class CollectionOptInPrompterTest {
    @Mock lateinit var mockActivity: Activity
    @Mock lateinit var mockViewGroup: ViewGroup

    @Mock lateinit var mockSharedPrefs: SharedPreferences

    private val packageName = "name"

    private lateinit var prompter: CollectionOptInPrompter

    @Before
    fun setup() {
        whenever(mockActivity.packageName).thenReturn(packageName)
        whenever(mockActivity.getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE))
                .thenReturn(mockSharedPrefs)

        prompter = CollectionOptInPrompter(mockActivity, mockViewGroup)
    }

    @Test
    fun `View snapshot`() {
        assertEquals(R.string.opt_in_help_prompt, prompter.initialPrompt)
        assertEquals(R.string.button_yes, prompter.initialPosBtnText)
        assertEquals(R.string.button_negative, prompter.initialNegBtnText)

        assertEquals(R.string.opt_in_error_collection_prompt, prompter.primaryRequest)
        assertEquals(R.string.button_yes, prompter.primaryPosBtnText)
        assertEquals(R.string.button_negative, prompter.primaryNegBtnText)

        assertEquals(R.string.opt_in_secondary_prompt, prompter.secondaryRequest)
        assertEquals(R.string.button_positive, prompter.secondaryPosBtnText)
        assertEquals(R.string.button_refuse, prompter.secondaryNegBtnText)
    }

    @Test
    fun `View should be show if it has not been`() {
        whenever(mockSharedPrefs.getBoolean(CollectionOptInPrompter.userHasBeenPromptedToOptIn, false))
                .thenReturn(false)

        val result = prompter.viewShouldBeShown()

        assertTrue(result)
    }

    @Test
    fun `View should be shown by default`() {
        val result = prompter.viewShouldBeShown()

        assertTrue(result)
    }

    @Test
    fun `View should not be shown if it has been`() {
        whenever(mockSharedPrefs.getBoolean(CollectionOptInPrompter.userHasBeenPromptedToOptIn, false))
                .thenReturn(true)

        val result = prompter.viewShouldBeShown()

        assertFalse(result)
    }

    @Test
    fun `userHasOptedIn delegates to preferences`() {
        whenever(mockSharedPrefs.getBoolean(CollectionOptInPrompter.userHasOptedInPreference, false))
                .thenReturn(false)

        val result = prompter.userHasOptedIn()

        assertFalse(result)
        verify(mockSharedPrefs).getBoolean(CollectionOptInPrompter.userHasOptedInPreference, false)
    }
}