package tech.ula.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.annotation.StringRes
import tech.ula.R

interface UserPrompter {
    @get: StringRes val initialPrompt: Int
    @get: StringRes val initialPosBtnText: Int
    @get: StringRes val initialNegBtnText: Int

    @get: StringRes val primaryRequest: Int
    @get: StringRes val primaryPosBtnText: Int
    @get: StringRes val primaryNegBtnText: Int
    val primaryPositiveBtnAction: () -> Unit

    @get: StringRes val secondaryRequest: Int
    @get: StringRes val secondaryPosBtnText: Int
    @get: StringRes val secondaryNegBtnText: Int
    val secondaryPositiveBtnAction: () -> Unit

    val finishedAction: () -> Unit

    fun viewShouldBeShown(): Boolean
    fun showView(viewGroup: ViewGroup, activity: Activity) {
        val view = activity.layoutInflater.inflate(R.layout.layout_user_prompt, null)
        val prompt = view.findViewById<TextView>(R.id.text_prompt)
        val posBtn = view.findViewById<Button>(R.id.btn_positive_response)
        val negBtn = view.findViewById<Button>(R.id.btn_negative_response)

        prompt.text = activity.getString(initialPrompt)

        posBtn.text = activity.getString(initialPosBtnText)
        posBtn.setOnClickListener initial@ {
            prompt.text = activity.getString(primaryRequest)

            posBtn.text = activity.getString(primaryPosBtnText)
            posBtn.setOnClickListener primary@ {
                primaryPositiveBtnAction()
                finishedAction()
                viewGroup.removeView(view)
            }

            negBtn.text = activity.getString(primaryNegBtnText)
            negBtn.setOnClickListener primary@ {
                finishedAction()
                viewGroup.removeView(view)
            }
        }

        negBtn.text = activity.getString(initialNegBtnText)
        negBtn.setOnClickListener initial@ {
            prompt.text = activity.getString(secondaryRequest)

            posBtn.text = activity.getString(secondaryPosBtnText)
            posBtn.setOnClickListener secondary@ {
                secondaryPositiveBtnAction()
                finishedAction()
                viewGroup.removeView(view)
            }

            negBtn.text = activity.getString(secondaryNegBtnText)
            negBtn.setOnClickListener secondary@ {
                finishedAction()
                viewGroup.removeView(view)
            }
        }

        viewGroup.addView(view)
    }
}

class UserFeedbackPrompter(private val activity: Activity) : UserPrompter {
    private val prefString = "usage"
    private val prefs = activity.getSharedPreferences(prefString, Context.MODE_PRIVATE)
    private val numberOfTimesOpenedKey = "numberOfTimesOpened"
    private val userGaveFeedbackKey = "userGaveFeedback"
    private val dateTimeFirstOpenKey = "dateTimeFirstOpen"
    private val millisecondsInThreeDays = 259200000L
    private val minimumNumberOfOpensBeforeReviewRequest = 15

    override val initialPrompt: Int
        get() = R.string.review_is_user_enjoying
    override val initialPosBtnText: Int
        get() = R.string.button_yes
    override val initialNegBtnText: Int
        get() = R.string.button_negative

    override val primaryRequest: Int
        get() = R.string.review_ask_for_rating
    override val primaryPosBtnText: Int
        get() = R.string.button_positive
    override val primaryNegBtnText: Int
        get() = R.string.button_refuse

    override val secondaryRequest: Int
        get() = R.string.review_ask_for_feedback
    override val secondaryPosBtnText: Int
        get() = R.string.button_positive
    override val secondaryNegBtnText: Int
        get() = R.string.button_negative

    override val primaryPositiveBtnAction: () -> Unit
        get() = sendReviewIntent
    override val secondaryPositiveBtnAction: () -> Unit
        get() = sendGithubIntent

    override val finishedAction: () -> Unit
        get() = userHasGivenFeedback

    override fun viewShouldBeShown(): Boolean {
        return true
//        return askingForFeedbackIsAppropriate()
    }

    private fun askingForFeedbackIsAppropriate(): Boolean {
        return getIsSufficientTimeElapsedSinceFirstOpen() && numberOfTimesOpenedIsGreaterThanThreshold() && !getUserGaveFeedback()
    }

    private fun incrementNumberOfTimesOpened(numberTimesOpened: Int) {
        with(prefs.edit()) {
            if (numberTimesOpened == 1) putLong(dateTimeFirstOpenKey, System.currentTimeMillis())
            putInt(numberOfTimesOpenedKey, numberTimesOpened + 1)
            apply()
        }
    }

    private val sendReviewIntent = {
        val userlandPlayStoreURI = "https://play.google.com/store/apps/details?id=tech.ula"
        val intent = Intent("android.intent.action.VIEW", Uri.parse(userlandPlayStoreURI))
        activity.startActivity(intent)
    }

    private val sendGithubIntent = {
        val githubURI = "https://github.com/CypherpunkArmory/UserLAnd"
        val intent = Intent("android.intent.action.VIEW", Uri.parse(githubURI))
        activity.startActivity(intent)
    }

    private val userHasGivenFeedback = {
        with(prefs.edit()) {
            putBoolean(userGaveFeedbackKey, true)
            apply()
        }
    }

    private fun getUserGaveFeedback(): Boolean {
        return prefs.getBoolean(userGaveFeedbackKey, false)
    }

    private fun getIsSufficientTimeElapsedSinceFirstOpen(): Boolean {
        val dateTimeFirstOpened = prefs.getLong(dateTimeFirstOpenKey, 0L)
        val dateTimeWithSufficientTimeElapsed = dateTimeFirstOpened + millisecondsInThreeDays

        return (System.currentTimeMillis() > dateTimeWithSufficientTimeElapsed)
    }

    private fun numberOfTimesOpenedIsGreaterThanThreshold(): Boolean {
        val numberTimesOpened = prefs.getInt(numberOfTimesOpenedKey, 0) + 1
        incrementNumberOfTimesOpened(numberTimesOpened)
        return numberTimesOpened > minimumNumberOfOpensBeforeReviewRequest
    }
}

class CollectionOptInPrompter(activity: Activity) : UserPrompter {
    companion object {
        const val userHasOptedInPreference = "pref_opt_in"
        const val userHasBeenPromptedToOptIn = "optInChecked"
    }

    private val prefs = activity.defaultSharedPreferences

    override val initialPrompt: Int
        get() = R.string.opt_in_help_prompt
    override val initialPosBtnText: Int
        get() = R.string.button_yes
    override val initialNegBtnText: Int
        get() = R.string.button_negative

    override val primaryRequest: Int
        get() = R.string.opt_in_error_collection_prompt
    override val primaryPosBtnText: Int
        get() = R.string.button_yes
    override val primaryNegBtnText: Int
        get() = R.string.button_negative

    override val secondaryRequest: Int
        get() = R.string.opt_in_secondary_prompt
    override val secondaryPosBtnText: Int
        get() = R.string.button_positive
    override val secondaryNegBtnText: Int
        get() = R.string.button_refuse

    override val primaryPositiveBtnAction: () -> Unit
        get() = setOptInOn
    override val secondaryPositiveBtnAction: () -> Unit
        get() = setOptInOn

    override val finishedAction: () -> Unit
        get() = userHasBeenPrompted

    override fun viewShouldBeShown(): Boolean {
        return true
//        return !prefs.getBoolean(userHasBeenPromptedToOptIn, false)
    }

    private val setOptInOn = {
        with (prefs.edit()) {
            putBoolean(userHasOptedInPreference, true)
            apply()
        }
    }

    private val userHasBeenPrompted = {
        with (prefs.edit()) {
            putBoolean(userHasBeenPromptedToOptIn, true)
            apply()
        }
    }
}