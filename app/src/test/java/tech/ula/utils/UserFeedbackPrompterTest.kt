package tech.ula.utils

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.* // ktlint-disable no-wildcard-imports
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.R

@RunWith(MockitoJUnitRunner::class)
class UserFeedbackPrompterTest {

    @Mock lateinit var mockActivity: Activity

    @Mock lateinit var mockSharedPrefs: SharedPreferences

    @Mock lateinit var mockSharedPrefsEditor: SharedPreferences.Editor

    private val minimumTimesAppOpenedToShowReview = 16

    lateinit var prompter: UserFeedbackPrompter

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

    @Before
    fun setup() {
        whenever(mockActivity.getSharedPreferences(UserFeedbackPrompter.prefString, Context.MODE_PRIVATE))
                .thenReturn(mockSharedPrefs)
        whenever(mockSharedPrefs.edit()).thenReturn(mockSharedPrefsEditor)
        prompter = UserFeedbackPrompter(mockActivity)
    }

    @Test
    fun `View snapshot`() {
        assertEquals(R.string.review_is_user_enjoying, prompter.initialPrompt)
        assertEquals(R.string.button_yes, prompter.initialPosBtnText)
        assertEquals(R.string.button_negative, prompter.initialNegBtnText)

        assertEquals(R.string.review_ask_for_rating, prompter.primaryRequest)
        assertEquals(R.string.button_positive, prompter.primaryPosBtnText)
        assertEquals(R.string.button_refuse, prompter.primaryNegBtnText)

        assertEquals(R.string.review_ask_for_feedback, prompter.secondaryRequest)
        assertEquals(R.string.button_positive, prompter.secondaryPosBtnText)
        assertEquals(R.string.button_negative, prompter.secondaryNegBtnText)
    }

    @Test
    fun `Asking for feedback is not appropriate if app opened for first time today`() {
        setTimesAppOpened(minimumTimesAppOpenedToShowReview)
        setUserGaveFeedback(false)
        appOpenedNumberOfDaysAgo(0)

        assertFalse(prompter.viewShouldBeShown())
    }

    @Test
    fun `Asking for feedback is appropriate if app opened four days ago, hasn't given feedback and opened the app at the minimum amount of times`() {
        setTimesAppOpened(minimumTimesAppOpenedToShowReview)
        setUserGaveFeedback(false)
        appOpenedNumberOfDaysAgo(4)

        assertTrue(prompter.viewShouldBeShown())
    }

    @Test
    fun `Asking for feedback is not appropriate if app opened two days ago`() {
        setTimesAppOpened(minimumTimesAppOpenedToShowReview)
        setUserGaveFeedback(false)
        appOpenedNumberOfDaysAgo(2)

        assertFalse(prompter.viewShouldBeShown())
    }

    @Test
    fun `Asking for feedback is not appropriate if user already gave feedback`() {
        setTimesAppOpened(minimumTimesAppOpenedToShowReview)
        setUserGaveFeedback(true)
        appOpenedNumberOfDaysAgo(3)

        assertFalse(prompter.viewShouldBeShown())
    }

    @Test
    fun `Asking for feedback is not appropriate when app wasn't opened more than the required amount`() {
        setTimesAppOpened(minimumTimesAppOpenedToShowReview - 5)
        setUserGaveFeedback(false)
        appOpenedNumberOfDaysAgo(3)

        assertFalse(prompter.viewShouldBeShown())
    }
}