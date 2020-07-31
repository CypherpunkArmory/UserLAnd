package tech.ula.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import com.android.billingclient.api.Purchase
import tech.ula.MainActivity
import tech.ula.R

interface UserPrompter {
    @get: StringRes val initialPrompt: Int
    @get: StringRes val initialPosBtnText: Int
    @get: StringRes val initialNegBtnText: Int
    val altInitialPosFlow: Boolean
    val initialPositiveBtnAction: () -> Unit

    @get: StringRes val primaryRequest: Int
    @get: StringRes val primaryPosBtnText: Int
    @get: StringRes val primaryNegBtnText: Int
    val primaryPositiveBtnAction: () -> Unit

    @get: StringRes val secondaryRequest: Int
    @get: StringRes val secondaryPosBtnText: Int
    @get: StringRes val secondaryNegBtnText: Int
    val secondaryPositiveBtnAction: () -> Unit

    val finishedAction: () -> Unit

    val savedActivity: Activity
    val savedViewGroup: ViewGroup

    fun viewShouldBeShown(): Boolean
    fun showView() {
        val view = savedActivity.layoutInflater.inflate(R.layout.layout_user_prompt, null)
        val prompt = view.findViewById<TextView>(R.id.text_prompt)
        val posBtn = view.findViewById<Button>(R.id.btn_positive_response)
        val negBtn = view.findViewById<Button>(R.id.btn_negative_response)

        prompt.text = savedActivity.getString(initialPrompt)

        posBtn.text = savedActivity.getString(initialPosBtnText)
        posBtn.setOnClickListener initial@{
            if (altInitialPosFlow) {
                initialPositiveBtnAction()
                finishedAction()
                savedViewGroup.removeView(view)
            } else {
                prompt.text = savedActivity.getString(primaryRequest)

                posBtn.text = savedActivity.getString(primaryPosBtnText)
                posBtn.setOnClickListener primary@{
                    primaryPositiveBtnAction()
                    finishedAction()
                    savedViewGroup.removeView(view)
                }

                negBtn.text = savedActivity.getString(primaryNegBtnText)
                negBtn.setOnClickListener primary@{
                    finishedAction()
                    savedViewGroup.removeView(view)
                }
            }
        }

        negBtn.text = savedActivity.getString(initialNegBtnText)
        negBtn.setOnClickListener initial@{
            prompt.text = savedActivity.getString(secondaryRequest)

            posBtn.text = savedActivity.getString(secondaryPosBtnText)
            posBtn.setOnClickListener secondary@{
                secondaryPositiveBtnAction()
                finishedAction()
                savedViewGroup.removeView(view)
            }

            negBtn.text = savedActivity.getString(secondaryNegBtnText)
            negBtn.setOnClickListener secondary@{
                finishedAction()
                savedViewGroup.removeView(view)
            }
        }

        savedViewGroup.addView(view)
    }
}

class UserFeedbackPrompter(private val activity: Activity, private val viewGroup: ViewGroup) : UserPrompter {
    companion object {
        const val prefString = "usage"
    }
    private val prefs = activity.getSharedPreferences(prefString, Context.MODE_PRIVATE)

    private val numberOfTimesOpenedKey = "numberOfTimesOpened"
    private val userGaveFeedbackKey = "userGaveFeedback"
    private val dateTimeFirstOpenKey = "dateTimeFirstOpen"
    private val millisecondsInThreeDays = 259200000L
    private val minimumNumberOfOpensBeforeReviewRequest = 15

    private val doNothing = {
    }
    override val savedActivity: Activity
        get() = activity
    override val savedViewGroup: ViewGroup
        get() = viewGroup
    override val altInitialPosFlow: Boolean
        get() = false
    override val initialPositiveBtnAction: () -> Unit
        get() = doNothing

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

    override fun viewShouldBeShown(): Boolean {
        return askingForFeedbackIsAppropriate()
    }

    private fun askingForFeedbackIsAppropriate(): Boolean {
        return getIsSufficientTimeElapsedSinceFirstOpen() &&
                numberOfTimesOpenedIsGreaterThanThreshold() &&
                !getUserGaveFeedback()
    }

    private fun getIsSufficientTimeElapsedSinceFirstOpen(): Boolean {
        val dateTimeFirstOpened = prefs.getLong(dateTimeFirstOpenKey, 0L)
        val dateTimeWithSufficientTimeElapsed = dateTimeFirstOpened + millisecondsInThreeDays

        return (System.currentTimeMillis() > dateTimeWithSufficientTimeElapsed)
    }

    private fun numberOfTimesOpenedIsGreaterThanThreshold(): Boolean {
        val numberTimesOpened = prefs.getInt(numberOfTimesOpenedKey, 0) + 1
        setNumberOfTimesOpened(numberTimesOpened)
        return numberTimesOpened > minimumNumberOfOpensBeforeReviewRequest
    }

    private fun setNumberOfTimesOpened(numberTimesOpened: Int) {
        with(prefs.edit()) {
            if (numberTimesOpened == 1) putLong(dateTimeFirstOpenKey, System.currentTimeMillis())
            putInt(numberOfTimesOpenedKey, numberTimesOpened)
            apply()
        }
    }

    private fun getUserGaveFeedback(): Boolean {
        return prefs.getBoolean(userGaveFeedbackKey, false)
    }
}

class CollectionOptInPrompter(private val activity: Activity, private val viewGroup: ViewGroup) : UserPrompter {
    private val prefs = activity.defaultSharedPreferences
    private val logger = SentryLogger()

    private val doNothing = {
    }
    override val savedActivity: Activity
        get() = activity
    override val savedViewGroup: ViewGroup
        get() = viewGroup
    override val altInitialPosFlow: Boolean
        get() = false
    override val initialPositiveBtnAction: () -> Unit
        get() = doNothing

    companion object {
        const val userHasOptedInPreference = "pref_opt_in"

        const val userHasBeenPromptedToOptIn = "opt_in_checked"
    }

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
        return !prefs.getBoolean(userHasBeenPromptedToOptIn, false)
    }

    fun userHasOptedIn(): Boolean {
        return prefs.getBoolean(userHasOptedInPreference, false)
    }

    private val setOptInOn = {
        logger.initialize(activity)
        with(prefs.edit()) {
            putBoolean(userHasOptedInPreference, true)
            apply()
        }
    }

    private val userHasBeenPrompted = {
        with(prefs.edit()) {
            putBoolean(userHasBeenPromptedToOptIn, true)
            apply()
        }
    }
}

class ContributionPrompter(private val activity: MainActivity, private val viewGroup: ViewGroup) : UserPrompter {
    private val numberOfTimesOpenedKey = "numberOfTimesOpenedContribution"
    private val hasMadeSubPurchaseKey = "hasMadeSubPurchase"
    private val hasMadeInAppPurchaseKey = "hasMadeInAppPurchase"
    private val minimumNumberOfOpensBeforeContributionRequest = 5
    private var subscriptionSupported = false

    private val doNothing = {
    }
    override val savedActivity: MainActivity
        get() = activity
    override val savedViewGroup: ViewGroup
        get() = viewGroup

    private val openContributionView = {
        val view = savedActivity.layoutInflater.inflate(R.layout.dia_contribution, null)
        val amountSeekBar = view.findViewById<SeekBar>(R.id.amountSeekBar)
        val chosenAmount = view.findViewById<TextView>(R.id.chosenAmountTextView)
        val processBtn = view.findViewById<Button>(R.id.processButton)
        val oneTimeRadioButton = view.findViewById<RadioButton>(R.id.oneTimeRadioButton)
        val yearlyRadioButton = view.findViewById<RadioButton>(R.id.yearlyRadioButton)
        val monthlyRadioButton = view.findViewById<RadioButton>(R.id.monthlyRadioButton)

        yearlyRadioButton.isEnabled = subscriptionSupported
        monthlyRadioButton.isEnabled = subscriptionSupported

        amountSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                when (progress) {
                    0 -> chosenAmount.text = "$1 USD"
                    1 -> chosenAmount.text = "$5 USD"
                    2 -> chosenAmount.text = "$10 USD"
                    3 -> chosenAmount.text = "$20 USD"
                    else -> chosenAmount.text = "invalid"
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // here we can write some code to do something whenever the user touche the seekbar
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // show some message after user stopped scrolling the seekbar
            }
        })

        processBtn.setOnClickListener initial@{
            var productId = ""
            when (amountSeekBar.progress) {
                0 -> productId = "1us"
                1 -> productId = "5us"
                2 -> productId = "10us"
                3 -> productId = "20us"
                else -> productId = "invalid"
            }
            if (oneTimeRadioButton.isChecked) {
                productId += "_onetime"
            } else { // it is a subscription purchase
                if (yearlyRadioButton.isChecked) {
                    productId += "_yearly"
                } else if (monthlyRadioButton.isChecked) {
                    productId += "_monthly"
                }
            }
            savedActivity.billingManager.startPurchaseFlow(productId)
            savedViewGroup.removeView(view)
        }

        savedViewGroup.addView(view)
    }
    override val altInitialPosFlow: Boolean
        get() = true
    override val initialPositiveBtnAction: () -> Unit
        get() = openContributionView

    companion object {
        const val prefString = "usage"
    }

    private val prefs = savedActivity.getSharedPreferences(prefString, Context.MODE_PRIVATE)

    val onSubscriptionSupportedChecked: (Boolean) -> Unit = {
        subscriptionSupported = it
    }

    val onEntitledSubPurchases: (List<Purchase>) -> Unit = {
        processSubPurchases(it)
    }

    val onEntitledInAppPurchases: (List<Purchase>) -> Unit = {
        processInAppPurchases(it)
    }

    val onPurchase: (Purchase) -> Unit = {
        processPurchase(it)
    }

    private fun processSubPurchases(purchases: List<Purchase>) {
        setHasMadeSubPurchase(!purchases.isEmpty())
    }

    private fun processInAppPurchases(purchases: List<Purchase>) {
        setHasMadeInAppPurchase(!purchases.isEmpty())
    }

    private fun processPurchase(purchase: Purchase) {
        if (purchase.sku.endsWith("onetime"))
            setHasMadeInAppPurchase(true)
        else
            setHasMadeSubPurchase(true)
        Toast.makeText(savedActivity, "Thanks for your contribution", Toast.LENGTH_LONG).show()
    }

    override val initialPrompt: Int
        get() = R.string.contribution_primary
    override val initialPosBtnText: Int
        get() = R.string.button_yes
    override val initialNegBtnText: Int
        get() = R.string.button_refuse

    override val primaryRequest: Int
        get() = R.string.contribution_secondary_positive
    override val primaryPosBtnText: Int
        get() = R.string.button_positive
    override val primaryNegBtnText: Int
        get() = R.string.button_refuse

    override val secondaryRequest: Int
        get() = R.string.contribution_secondary_negative
    override val secondaryPosBtnText: Int
        get() = R.string.button_yes
    override val secondaryNegBtnText: Int
        get() = R.string.button_refuse

    override val primaryPositiveBtnAction: () -> Unit
        get() = doNothing
    override val secondaryPositiveBtnAction: () -> Unit
        get() = sendGithubIntent

    override val finishedAction: () -> Unit
        get() = userHasResponded

    private val sendGithubIntent = {
        val githubURI = "https://github.com/CypherpunkArmory/UserLAnd/wiki/FAQ"
        val intent = Intent("android.intent.action.VIEW", Uri.parse(githubURI))
        savedActivity.startActivity(intent)
    }

    private val userHasResponded = {
        with(prefs.edit()) {
            putInt(numberOfTimesOpenedKey, 0)
            apply()
        }
    }

    override fun viewShouldBeShown(): Boolean {
        return askingForContributionIsAppropriate()
    }

    private fun askingForContributionIsAppropriate(): Boolean {
        return numberOfTimesOpenedIsGreaterThanThreshold() && !(hasMadeSubPurchase() || hasMadeInAppPurchase())
    }

    private fun numberOfTimesOpenedIsGreaterThanThreshold(): Boolean {
        val numberTimesOpened = prefs.getInt(numberOfTimesOpenedKey, 0) + 1
        setNumberOfTimesOpened(numberTimesOpened)
        return numberTimesOpened > minimumNumberOfOpensBeforeContributionRequest
    }

    private fun setNumberOfTimesOpened(numberTimesOpened: Int) {
        with(prefs.edit()) {
            putInt(numberOfTimesOpenedKey, numberTimesOpened)
            apply()
        }
    }

    private fun hasMadeSubPurchase(): Boolean {
        return prefs.getBoolean(hasMadeSubPurchaseKey, false)
    }

    private fun hasMadeInAppPurchase(): Boolean {
        return prefs.getBoolean(hasMadeInAppPurchaseKey, false)
    }

    private fun setHasMadeSubPurchase(hasMadePurchase: Boolean) {
        with(prefs.edit()) {
            putBoolean(hasMadeSubPurchaseKey, hasMadePurchase)
            if (hasMadePurchase)
                putInt(numberOfTimesOpenedKey, 0)
            apply()
        }
    }

    private fun setHasMadeInAppPurchase(hasMadePurchase: Boolean) {
        with(prefs.edit()) {
            putBoolean(hasMadeInAppPurchaseKey, hasMadePurchase)
            if (hasMadePurchase)
                putInt(numberOfTimesOpenedKey, 0)
            apply()
        }
    }
}
