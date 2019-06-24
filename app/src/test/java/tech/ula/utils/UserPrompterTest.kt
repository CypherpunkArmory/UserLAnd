package tech.ula.utils

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class UserPrompterTest {

    @Mock lateinit var mockActivity: Activity

    @Mock lateinit var mockSharedPrefs: SharedPreferences

    @Mock lateinit var mockSharedPrefsEditor: SharedPreferences.Editor

    private val minimumTimesAppOpenedToShowReview = 16

    lateinit var prompter: UserPrompter

    private fun appOpenedNumberOfDaysAgo(days: Int) {
        val millisecondsInOneDay = 86400000L
        val millisecondsNow = System.currentTimeMillis()
        whenever(mockSharedPrefs.getLong("dateTimeFirstOpen", 0))
                .thenReturn(millisecondsNow - (millisecondsInOneDay * days))
    }

    private fun setTimesAppOpened(numberOfTimesOpened: Int) {
        whenever(mockSharedPrefs.getInt("numberOfTimesOpened", 0))
                .thenReturn(numberOfTimesOpened)
    }

    private fun setUserGaveFeedback(userGaveFeedback: Boolean) {
        whenever(mockSharedPrefs.getBoolean("userGaveFeedback", false))
                .thenReturn(userGaveFeedback)
    }

    private fun initUserFeedbackPrompter() {
        whenever(mockActivity.getSharedPreferences(UserFeedbackPrompter.prefString, Context.MODE_PRIVATE))
                .thenReturn(mockSharedPrefs)
        whenever(mockSharedPrefs.edit()).thenReturn(mockSharedPrefsEditor)
        prompter = UserFeedbackPrompter(mockActivity)
    }

    @Test
    fun `Asking for feedback is not appropriate if app opened for first time today`() {
        setTimesAppOpened(minimumTimesAppOpenedToShowReview)
        setUserGaveFeedback(false)
        appOpenedNumberOfDaysAgo(0)

        initUserFeedbackPrompter()

        assertFalse(prompter.viewShouldBeShown())
    }

    @Test
    fun `Asking for feedback is appropriate if app opened four days ago, hasn't given feedback and opened the app at the minimum amount of times`() {
        setTimesAppOpened(minimumTimesAppOpenedToShowReview)
        setUserGaveFeedback(false)
        appOpenedNumberOfDaysAgo(4)

        initUserFeedbackPrompter()

        assertTrue(prompter.viewShouldBeShown())
    }

    @Test
    fun `Asking for feedback is not appropriate if app opened two days ago`() {
        setTimesAppOpened(minimumTimesAppOpenedToShowReview)
        setUserGaveFeedback(false)
        appOpenedNumberOfDaysAgo(2)

        initUserFeedbackPrompter()

        assertFalse(prompter.viewShouldBeShown())
    }

    @Test
    fun `Asking for feedback is not appropriate if user already gave feedback`() {
        setTimesAppOpened(minimumTimesAppOpenedToShowReview)
        setUserGaveFeedback(true)
        appOpenedNumberOfDaysAgo(3)

        initUserFeedbackPrompter()

        assertFalse(prompter.viewShouldBeShown())
    }

    @Test
    fun `Asking for feedback is not appropriate when app wasn't opened more than the required amount`() {
        setTimesAppOpened(minimumTimesAppOpenedToShowReview - 5)
        setUserGaveFeedback(false)
        appOpenedNumberOfDaysAgo(3)

        initUserFeedbackPrompter()

        assertFalse(prompter.viewShouldBeShown())
    }
}