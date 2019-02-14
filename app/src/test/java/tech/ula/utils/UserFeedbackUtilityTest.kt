package tech.ula.utils

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
class UserFeedbackUtilityTest {

    @Mock
    lateinit var userFeedbackUtility: UserFeedbackUtility

    @Mock
    lateinit var sharedPrefs: SharedPreferences

    private val minimumTimesAppOpenedToShowReview = 16

    @Before
    fun setup() {
        userFeedbackUtility = UserFeedbackUtility(sharedPrefs)
    }

    private fun appOpenedNumberOfDaysAgo(days: Int) {
        val millisecondsInOneDay = 86400000L
        val millisecondsNow = System.currentTimeMillis()
        whenever(sharedPrefs.getLong("dateTimeFirstOpen", 0))
                .thenReturn(millisecondsNow - (millisecondsInOneDay * days))
    }

    private fun setTimesAppOpened(numberOfTimesOpened: Int) {
        whenever(sharedPrefs.getInt("numberOfTimesOpened", 1))
                .thenReturn(numberOfTimesOpened)
    }

    private fun setUserGaveFeedback(userGaveFeedback: Boolean) {
        whenever(sharedPrefs.getBoolean("userGaveFeedback", false))
                .thenReturn(userGaveFeedback)
    }

    @Test
    fun `Asking for feedback is not appropriate if app opened for first time today`() {
        setTimesAppOpened(minimumTimesAppOpenedToShowReview)
        setUserGaveFeedback(false)
        appOpenedNumberOfDaysAgo(0)

        assertFalse(userFeedbackUtility.askingForFeedbackIsAppropriate())
    }

    @Test
    fun `Asking for feedback is appropriate if app opened if app opened four days ago`() {
        setTimesAppOpened(minimumTimesAppOpenedToShowReview)
        setUserGaveFeedback(false)
        appOpenedNumberOfDaysAgo(4)

        assertTrue(userFeedbackUtility.askingForFeedbackIsAppropriate())
    }

    @Test
    fun `Asking for feedback is not appropriate if app opened if app opened two days ago`() {
        setTimesAppOpened(minimumTimesAppOpenedToShowReview)
        setUserGaveFeedback(false)
        appOpenedNumberOfDaysAgo(2)

        assertFalse(userFeedbackUtility.askingForFeedbackIsAppropriate())
    }

    @Test
    fun `Asking for feedback is not appropriate if user already gave feedback`() {
        setTimesAppOpened(minimumTimesAppOpenedToShowReview)
        setUserGaveFeedback(true)
        appOpenedNumberOfDaysAgo(3)

        assertFalse(userFeedbackUtility.askingForFeedbackIsAppropriate())
    }

    @Test
    fun `Asking for feedback is not appropriate when app wasn't opened more than the required amount`() {
        setTimesAppOpened(minimumTimesAppOpenedToShowReview - 5)
        setUserGaveFeedback(false)
        appOpenedNumberOfDaysAgo(3)

        assertFalse(userFeedbackUtility.askingForFeedbackIsAppropriate())
    }
}