package tech.ula.utils

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.BillingClient.BillingResponse
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.Purchase
import tech.ula.BuildConfig

class PlayServiceManager(private val playServicesUpdateListener: PlayServicesUpdateListener) : PurchasesUpdatedListener {

    private lateinit var billingClient: BillingClient

    private val appsYearlySubId = "apps_yearly_subscription"
    private val packageName = "tech.ula"

    interface PlayServicesUpdateListener {
        fun onSubscriptionsAreNotSupported()
        fun onBillingClientConnectionChange(isConnected: Boolean)
        fun onSubscriptionPurchased()
        fun onPlayServiceError()
    }

    override fun onPurchasesUpdated(@BillingResponse responseCode: Int, purchases: MutableList<Purchase>?) {
        if (responseCode == BillingResponse.OK) {
            purchases?.forEach {
                if (purchaseIsForYearlyAppsSub(it)) {
                    playServicesUpdateListener.onSubscriptionPurchased()
                }
            }
        } else if (responseCode == BillingResponse.USER_CANCELED) return
        else playServicesUpdateListener.onPlayServiceError()
    }

    fun startBillingClient(activity: Activity) {
        if (!BuildConfig.ENABLE_PLAY_SERVICES) return
        billingClient = BillingClient.newBuilder(activity).setListener(this).build()
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(responseCode: Int) {
                if (responseCode == BillingClient.BillingResponse.OK) {
                    playServicesUpdateListener.onBillingClientConnectionChange(isConnected = true)
                }
            }

            override fun onBillingServiceDisconnected() {
                playServicesUpdateListener.onBillingClientConnectionChange(isConnected = false)
            }
        })
    }

    fun startBillingFlow(activity: Activity) {
        if (!BuildConfig.ENABLE_PLAY_SERVICES) {
            playServicesUpdateListener.onSubscriptionPurchased()
            return
        }
        if (!subscriptionsAreSupported()) {
            playServicesUpdateListener.onSubscriptionsAreNotSupported()
        }
        val flowParams = BillingFlowParams.newBuilder()
                .setSku(appsYearlySubId)
                .setType(BillingClient.SkuType.SUBS)
                .build()
        billingClient.launchBillingFlow(activity, flowParams)
    }

    fun userHasYearlyAppsSubscription(): Boolean {
        if (!BuildConfig.ENABLE_PLAY_SERVICES) return true // Always have subscriptions if debugging
        return cacheIndicatesUserHasPurchasedSubscription()
    }

    private fun cacheIndicatesUserHasPurchasedSubscription(): Boolean {
        return billingClient.queryPurchases(BillingClient.SkuType.SUBS).purchasesList.any {
            purchaseIsForYearlyAppsSub(it)
        }
    }

    private fun purchaseIsForYearlyAppsSub(purchase: Purchase): Boolean {
        return purchase.sku == appsYearlySubId && purchase.packageName == packageName
    }

    private fun subscriptionsAreSupported(): Boolean {
        val responseCode = billingClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS)
        return responseCode == BillingResponse.OK
    }
}