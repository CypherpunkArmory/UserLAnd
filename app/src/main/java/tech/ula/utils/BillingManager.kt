package tech.ula.utils

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.FeatureType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.SkuDetails
import com.android.billingclient.api.SkuDetailsParams
import java.util.*
import kotlin.collections.HashMap

/**
 * When using this class:
 * - Call `queryPurchases()` in your Activity's onResume() method
 * - Call `query*SubscriptionSkuDetails()` when you want to show your in-app products
 * - Call `startPurchaseFlow()` when one of your in-app products is clicked on
 * - Call `destroy()` in your Activity's onDestroy() method
 */
class BillingManager(
    private val activity: Activity,
    private val onEntitledSubPurchases: (List<Purchase>) -> Unit,
    private val onEntitledInAppPurchases: (List<Purchase>) -> Unit,
    private val onPurchase: (Purchase) -> Unit,
    private val onSubscriptionSupportedChecked: (Boolean) -> Unit
) {

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingResponseCode.OK -> {
                purchases?.let {
                    for (purchase in purchases) {
                        when (purchase.purchaseState) {
                            Purchase.PurchaseState.PURCHASED -> {
                                onPurchase(purchase)
                                if (!purchase.isAcknowledged) {
                                    val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                                        .setPurchaseToken(purchase.purchaseToken)
                                        .build()
                                    billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                                        log("acknowledgePurchase(), billingResult=$billingResult")
                                    }
                                }
                            }
                            Purchase.PurchaseState.PENDING -> {
                                // Here you can confirm to the user that they've started the pending
                                // purchase, and to complete it, they should follow instructions that
                                // are given to them. You can also choose to remind the user in the
                                // future to complete the purchase if you detect that it is still
                                // pending.
                            }
                        }
                    }
                }
                log("onPurchasesUpdated(), $purchases")
            }
            BillingResponseCode.USER_CANCELED -> log("onPurchasesUpdated() - user cancelled the purchase flow - skipping")
            else -> log("onPurchasesUpdated() got unknown resultCode: ${billingResult.responseCode}")
        }
    }

    private val skuDetailsMap = HashMap<String, SkuDetails>()

    private val billingClient: BillingClient = BillingClient.newBuilder(activity)
        .enablePendingPurchases()
        .setListener(purchasesUpdatedListener)
        .build()

    private var isBillingServiceConnected = false

    val populateSkus: (List<SkuDetails>) -> Unit = {
        it.forEach { skuDetailsMap.put(it.sku, it) }
    }
    private fun handlePopulateSkuError(code: Int, message: String) {
        log("Error trying to populate skus.  code: $code message: $message")
    }

    init {
        startServiceConnection {
            onSubscriptionSupportedChecked(isSubscriptionPurchaseSupported())
            querySubPurchases()
            queryInAppPurchases()
            querySubscriptionSkuDetails(listOf(Sku.US1_MONTHLY, Sku.US5_MONTHLY, Sku.US10_MONTHLY, Sku.US20_MONTHLY, Sku.US1_YEARLY, Sku.US5_YEARLY, Sku.US10_YEARLY, Sku.US20_YEARLY), populateSkus, ::handlePopulateSkuError)
            queryInAppSkuDetails(listOf(Sku.US1_ONETIME, Sku.US5_ONETIME, Sku.US10_ONETIME, Sku.US20_ONETIME), populateSkus, ::handlePopulateSkuError)
        }
    }

    fun querySubPurchases() {
        if (isSubscriptionPurchaseSupported()) {
            val purchasesResult = billingClient.queryPurchases(BillingClient.SkuType.SUBS)
            if (purchasesResult.responseCode == BillingResponseCode.OK) {
                onEntitledSubPurchases(Collections.unmodifiableList(purchasesResult.purchasesList))
            } else {
                log("Error trying to query purchases: $purchasesResult")
            }
        }
    }

    fun queryInAppPurchases() {
        val purchasesResult = billingClient.queryPurchases(BillingClient.SkuType.INAPP)
        if (purchasesResult.responseCode == BillingResponseCode.OK) {
            onEntitledInAppPurchases(Collections.unmodifiableList(purchasesResult.purchasesList))
        } else {
            log("Error trying to query purchases: $purchasesResult")
        }
    }

    fun startPurchaseFlow(productId: String) {
        val sku = skuDetailsMap.get(productId)
        if (sku != null) {
            startServiceConnection {
                val flowParams = BillingFlowParams.newBuilder().setSkuDetails(sku).build()
                val billingResult = billingClient.launchBillingFlow(activity, flowParams)
                log("startPurchaseFlow(...), billingResult=$billingResult")
            }
        }
    }

    fun destroy() {
        log("destroy()")
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }

    private fun startServiceConnection(task: () -> Unit) {
        if (isBillingServiceConnected) {
            task()
        } else {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    log("onBillingSetupFinished(...), billingResult=$billingResult")
                    if (billingResult.responseCode == BillingResponseCode.OK) {
                        isBillingServiceConnected = true
                        task()
                    }
                }

                override fun onBillingServiceDisconnected() {
                    log("onBillingServiceDisconnected()")
                    isBillingServiceConnected = false
                    // Try to restart the connection on the next request to
                    // Google Play by calling the startConnection() method.
                }
            })
        }
    }

    private fun querySubscriptionSkuDetails(skus: List<String>, onSuccess: (List<SkuDetails>) -> Unit, onError: (code: Int, message: String) -> Unit) {
        val params = SkuDetailsParams.newBuilder().setSkusList(skus).setType(BillingClient.SkuType.SUBS)
        billingClient.querySkuDetailsAsync(params.build()) { billingResult, skuDetailsList ->
            if (billingResult.responseCode == BillingResponseCode.OK && skuDetailsList != null) {
                onSuccess(skuDetailsList)
            } else {
                onError(billingResult.responseCode, billingResult.debugMessage)
            }
        }
    }

    private fun queryInAppSkuDetails(skus: List<String>, onSuccess: (List<SkuDetails>) -> Unit, onError: (code: Int, message: String) -> Unit) {
        val params = SkuDetailsParams.newBuilder().setSkusList(skus).setType(BillingClient.SkuType.INAPP)
        billingClient.querySkuDetailsAsync(params.build()) { billingResult, skuDetailsList ->
            if (billingResult.responseCode == BillingResponseCode.OK && skuDetailsList != null) {
                onSuccess(skuDetailsList)
            } else {
                onError(billingResult.responseCode, billingResult.debugMessage)
            }
        }
    }

    private fun isSubscriptionPurchaseSupported(): Boolean {
        val response = billingClient.isFeatureSupported(FeatureType.SUBSCRIPTIONS)
        if (response.responseCode != BillingResponseCode.OK) {
            log("isSubscriptionPurchaseSupported(), not supported, error response: $response")
        }
        return response.responseCode == BillingResponseCode.OK
    }

    private fun log(message: String) {
        Log.d("BillingManager", message)
    }

    /** The format of SKUs must start with number or lowercase letter and can contain only numbers (0-9),
     * lowercase letters (a-z), underscores (_) & periods (.).*/
    object Sku {
        const val US1_ONETIME = "1us_onetime"
        const val US5_ONETIME = "5us_onetime"
        const val US10_ONETIME = "10us_onetime"
        const val US20_ONETIME = "20us_onetime"
        const val US1_MONTHLY = "1us_monthly"
        const val US5_MONTHLY = "5us_monthly"
        const val US10_MONTHLY = "10us_monthly"
        const val US20_MONTHLY = "20us_monthly"
        const val US1_YEARLY = "1us_yearly"
        const val US5_YEARLY = "5us_yearly"
        const val US10_YEARLY = "10us_yearly"
        const val US20_YEARLY = "20us_yearly"
        // Testing
        // const val TEST_PURCHASED = "android.test.purchased"
        // const val TEST_CANCELED = "android.test.canceled"
        // const val TEST_UNAVAILABLE = "android.test.item_unavailable"
    }
}
