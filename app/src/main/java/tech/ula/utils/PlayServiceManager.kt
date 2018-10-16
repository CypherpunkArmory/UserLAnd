package tech.ula.utils

import android.app.Activity
import android.content.pm.PackageManager
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.BillingClient.BillingResponse
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.Purchase
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import tech.ula.BuildConfig

class PlayServiceManager(private val playServicesUpdateListener: PlayServicesUpdateListener) : PurchasesUpdatedListener {

    private lateinit var billingClient: BillingClient

    private val appsYearlySubId = "apps_yearly_subscription"
    private val packageName = "tech.ula"
    private val playServicesResolutionRequest = 9000 // Arbitrary

    interface PlayServicesUpdateListener {
        fun onSubscriptionsAreNotSupported()
        fun onBillingClientConnectionChange(isConnected: Boolean)
        fun onSubscriptionPurchased()
        fun onPlayServiceError()
    }

    override fun onPurchasesUpdated(responseCode: Int, purchases: MutableList<Purchase>?) {
        if (responseCode == BillingResponse.OK) {
            purchases?.forEach {
                if (purchaseIsForYearlyAppsSub(it)) {
                    playServicesUpdateListener.onSubscriptionPurchased()
                }
            }
        } else if (responseCode == BillingResponse.USER_CANCELED) return
        else playServicesUpdateListener.onPlayServiceError()
    }

    fun playStoreIsAvailable(packageManager: PackageManager): Boolean {
        if (!BuildConfig.ENABLE_PLAY_SERVICES) return true
        val playStorePackageName = "com.android.vending"
        return try {
            packageManager.getPackageInfo(playStorePackageName, 0)
            true
        } catch (err: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun playServicesAreAvailable(activity: Activity): Boolean {
        if (!BuildConfig.ENABLE_PLAY_SERVICES) return true
        val googleApi = GoogleApiAvailability.getInstance()
        val result = googleApi.isGooglePlayServicesAvailable(activity)
        if (result != ConnectionResult.SUCCESS) {
            if (googleApi.isUserResolvableError(result)) {
                googleApi.getErrorDialog(activity, result, playServicesResolutionRequest).show()
            }
            return false
        }
        return true
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

    // TODO check this before requesting user donations
    private fun cacheIndicatesUserHasPurchasedSubscription(): Boolean {
        // Purchases list is nullable even though not documented as such. Null *at least* when play
        // store is unavailable
        val purchasesList = billingClient.queryPurchases(BillingClient.SkuType.SUBS).purchasesList
        return purchasesList?.any {
            purchaseIsForYearlyAppsSub(it)
        } ?: false
    }

    private fun purchaseIsForYearlyAppsSub(purchase: Purchase): Boolean {
        return purchase.sku == appsYearlySubId && purchase.packageName == packageName
    }

    private fun subscriptionsAreSupported(): Boolean {
        val responseCode = billingClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS)
        return responseCode == BillingResponse.OK
    }
}