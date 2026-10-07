package com.rabidstudios.punchtheclown

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams

/**
 * M5.2 Google Play Billing foundation for the permanent Remove Ads purchase.
 *
 * The Play Console product is intentionally queried as a one-time INAPP product.
 * Purchase launching, acknowledgement, restore, and entitlement enforcement are
 * added after the remove_ads product is created and activated in Play Console.
 */
class BillingManager(
    private val activity: Activity
) : PurchasesUpdatedListener {

    companion object {
        const val REMOVE_ADS_PRODUCT_ID = "remove_ads"
    }

    private val billingClient = BillingClient.newBuilder(activity)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    var billingReady: Boolean = false
        private set

    var removeAdsProductDetails: ProductDetails? = null
        private set

    fun start() {
        if (billingClient.isReady) {
            billingReady = true
            queryRemoveAdsProduct()
            return
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                billingReady =
                    billingResult.responseCode == BillingClient.BillingResponseCode.OK
                if (billingReady) {
                    queryRemoveAdsProduct()
                }
            }

            override fun onBillingServiceDisconnected() {
                billingReady = false
                // Auto service reconnection is enabled; the next billing call can reconnect.
            }
        })
    }

    fun stop() {
        billingReady = false
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }

    private fun queryRemoveAdsProduct() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(REMOVE_ADS_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, result ->
            removeAdsProductDetails =
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    result.productDetailsList.firstOrNull {
                        it.productId == REMOVE_ADS_PRODUCT_ID
                    }
                } else {
                    null
                }
        }
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: List<Purchase>?
    ) {
        // M5.3 will verify/acknowledge purchases and persist the Remove Ads entitlement.
    }
}
