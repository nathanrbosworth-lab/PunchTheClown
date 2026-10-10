package com.rabidstudios.punchtheclown

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Google Play Billing integration for the permanent Remove Ads entitlement.
 *
 * remove_ads is a non-consumable one-time INAPP product. Google Play purchase
 * state is authoritative; a local entitlement cache keeps known owners ad-free
 * while Play is temporarily unavailable. Purchases are never consumed.
 */
class BillingManager(
    private val activity: Activity,
    private val onStateChanged: () -> Unit = {},
    private val onUserMessage: (String) -> Unit = {}
) : PurchasesUpdatedListener {

    companion object {
        const val REMOVE_ADS_PRODUCT_ID = "remove_ads"
        const val REMOVE_ADS_PURCHASE_OPTION_ID = "remove-ads-buy"
        private const val PREF_REMOVE_ADS_OWNED = "remove_ads_owned"
    }

    private val prefs =
        activity.getSharedPreferences("punch_the_clown", Activity.MODE_PRIVATE)

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

    var productQueryComplete: Boolean = false
        private set

    var removeAdsProductDetails: ProductDetails? = null
        private set

    var isRemoveAdsOwned: Boolean =
        prefs.getBoolean(PREF_REMOVE_ADS_OWNED, false)
        private set

    var isRemoveAdsPending: Boolean = false
        private set

    val removeAdsPrice: String?
        get() = selectedRemoveAdsOffer()?.formattedPrice

    val canPurchaseRemoveAds: Boolean
        get() =
            billingReady &&
                !isRemoveAdsOwned &&
                !isRemoveAdsPending &&
                removeAdsProductDetails != null &&
                selectedRemoveAdsOffer() != null

    fun start() {
        if (billingClient.isReady) {
            billingReady = true
            notifyStateChanged()
            queryRemoveAdsProduct()
            queryOwnedPurchases()
            return
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                billingReady =
                    billingResult.responseCode == BillingClient.BillingResponseCode.OK
                notifyStateChanged()
                if (billingReady) {
                    queryRemoveAdsProduct()
                    queryOwnedPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                billingReady = false
                notifyStateChanged()
                // Auto service reconnection is enabled; refresh() retries when needed.
            }
        })
    }

    fun refresh() {
        if (billingClient.isReady) {
            billingReady = true
            queryRemoveAdsProduct()
            queryOwnedPurchases()
        } else {
            start()
        }
    }

    fun restoreRemoveAdsPurchase() {
        if (!billingClient.isReady) {
            notifyUser("Connecting to Google Play. Try Restore Purchase again in a moment.")
            start()
            return
        }
        queryOwnedPurchases(showRestoreResult = true)
    }

    fun launchRemoveAdsPurchase() {
        if (isRemoveAdsOwned) {
            notifyUser("Remove Ads is already owned on this Google Play account.")
            return
        }
        if (isRemoveAdsPending) {
            notifyUser("Your Remove Ads purchase is still pending in Google Play.")
            return
        }
        if (!billingClient.isReady) {
            notifyUser("Google Play Billing is still connecting. Try again in a moment.")
            start()
            return
        }

        val details = removeAdsProductDetails
        val offer = selectedRemoveAdsOffer()
        if (details == null || offer == null) {
            queryRemoveAdsProduct()
            notifyUser("Remove Ads is still loading from Google Play. Try again in a moment.")
            return
        }

        val offerToken = offer.offerToken
        if (offerToken.isNullOrBlank()) {
            queryRemoveAdsProduct()
            notifyUser("Remove Ads is not available from Google Play right now.")
            return
        }

        val productParams =
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .setOfferToken(offerToken)
                .build()

        val params =
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build()

        val result = billingClient.launchBillingFlow(activity, params)
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                queryOwnedPurchases(showRestoreResult = true)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                notifyUser("Purchase cancelled.")
            }
            else -> {
                notifyUser(
                    result.debugMessage.ifBlank {
                        "Google Play couldn't start the Remove Ads purchase."
                    }
                )
            }
        }
    }

    fun stop() {
        billingReady = false
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }

    private fun queryRemoveAdsProduct() {
        if (!billingClient.isReady) return

        productQueryComplete = false
        notifyStateChanged()

        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(REMOVE_ADS_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, result ->
            productQueryComplete = true
            removeAdsProductDetails =
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    result.productDetailsList.firstOrNull {
                        it.productId == REMOVE_ADS_PRODUCT_ID
                    }
                } else {
                    null
                }
            notifyStateChanged()
        }
    }

    private fun queryOwnedPurchases(showRestoreResult: Boolean = false) {
        if (!billingClient.isReady) return

        val params =
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(
                    purchases = purchases,
                    authoritative = true,
                    notifyPurchaseResult = false
                )
                if (showRestoreResult) {
                    when {
                        isRemoveAdsOwned ->
                            notifyUser("Remove Ads purchase restored.")
                        isRemoveAdsPending ->
                            notifyUser("Your Remove Ads purchase is still pending in Google Play.")
                        else ->
                            notifyUser(
                                "No Remove Ads purchase was found for this Google Play account."
                            )
                    }
                }
            } else if (showRestoreResult) {
                notifyUser(
                    billingResult.debugMessage.ifBlank {
                        "Google Play couldn't check your purchases."
                    }
                )
            }
        }
    }

    private fun selectedRemoveAdsOffer(): ProductDetails.OneTimePurchaseOfferDetails? {
        val details = removeAdsProductDetails ?: return null
        val offers = details.oneTimePurchaseOfferDetailsList

        return offers?.firstOrNull {
            it.purchaseOptionId == REMOVE_ADS_PURCHASE_OPTION_ID && it.offerId == null
        } ?: offers?.firstOrNull {
            it.purchaseOptionId == REMOVE_ADS_PURCHASE_OPTION_ID
        } ?: details.oneTimePurchaseOfferDetails
    }

    private fun processPurchases(
        purchases: List<Purchase>,
        authoritative: Boolean,
        notifyPurchaseResult: Boolean
    ) {
        val removeAdsPurchases =
            purchases.filter { purchase ->
                purchase.products.contains(REMOVE_ADS_PRODUCT_ID)
            }

        val purchased =
            removeAdsPurchases.firstOrNull {
                it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
        val pending =
            removeAdsPurchases.any {
                it.purchaseState == Purchase.PurchaseState.PENDING
            }

        if (purchased != null) {
            val wasOwned = isRemoveAdsOwned
            setEntitlement(owned = true, pending = false)

            if (!purchased.isAcknowledged) {
                acknowledgeRemoveAdsPurchase(purchased)
            }

            if (notifyPurchaseResult && !wasOwned) {
                notifyUser("Thank you! Ads are permanently removed.")
            }
            return
        }

        if (pending) {
            setEntitlement(owned = false, pending = true)
            if (notifyPurchaseResult) {
                notifyUser(
                    "Purchase pending. Ads will be removed after Google Play confirms payment."
                )
            }
            return
        }

        if (authoritative) {
            // A successful Play query is authoritative, including refunds/revocations.
            setEntitlement(owned = false, pending = false)
        }
    }

    private fun acknowledgeRemoveAdsPurchase(purchase: Purchase) {
        val params =
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

        billingClient.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                // Keep the entitlement because Play already reports PURCHASED.
                // The next foreground refresh will retry acknowledgment.
                queryOwnedPurchases()
            }
        }
    }

    private fun setEntitlement(owned: Boolean, pending: Boolean) {
        val changed =
            owned != isRemoveAdsOwned || pending != isRemoveAdsPending

        isRemoveAdsOwned = owned
        isRemoveAdsPending = pending

        if (prefs.getBoolean(PREF_REMOVE_ADS_OWNED, false) != owned) {
            prefs.edit().putBoolean(PREF_REMOVE_ADS_OWNED, owned).apply()
        }

        if (changed) notifyStateChanged()
    }

    private fun notifyStateChanged() {
        activity.runOnUiThread { onStateChanged() }
    }

    private fun notifyUser(message: String) {
        activity.runOnUiThread { onUserMessage(message) }
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: List<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                processPurchases(
                    purchases = purchases.orEmpty(),
                    authoritative = false,
                    notifyPurchaseResult = true
                )
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                notifyUser("Purchase cancelled.")
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                queryOwnedPurchases(showRestoreResult = true)
            }
            else -> {
                notifyUser(
                    billingResult.debugMessage.ifBlank {
                        "Google Play couldn't complete the Remove Ads purchase."
                    }
                )
            }
        }
    }
}
