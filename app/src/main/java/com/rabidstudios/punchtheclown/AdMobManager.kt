package com.rabidstudios.punchtheclown

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * M5 rewarded-ad and non-gameplay banner manager.
 *
 * Rewarded ads are preloaded and shown only after gameplay has already stopped.
 * Debug/test APKs always use Google's rewarded test unit so development traffic
 * can never hit the production rewarded placements.
 */
class AdMobManager(
    private val activity: Activity,
    private val adsRemoved: () -> Boolean = { false }
) {
    enum class RewardPlacement {
        PUNCH_CONTINUE,
        BEANING_CONTINUE
    }

    private val rewardedAds = mutableMapOf<RewardPlacement, RewardedAd>()
    private val loadingPlacements = mutableSetOf<RewardPlacement>()
    private var initialized = false
    private var initializing = false

    fun initialize() {
        if (initialized || initializing || adsRemoved()) return
        initializing = true
        MobileAds.initialize(activity) {
            initializing = false
            initialized = true
            if (!adsRemoved()) {
                preload(RewardPlacement.PUNCH_CONTINUE)
                preload(RewardPlacement.BEANING_CONTINUE)
            }
        }
    }

    fun onEntitlementChanged() {
        if (adsRemoved()) {
            rewardedAds.clear()
            return
        }

        if (initialized) {
            preload(RewardPlacement.PUNCH_CONTINUE)
            preload(RewardPlacement.BEANING_CONTINUE)
        } else {
            initialize()
        }
    }

    fun preload(placement: RewardPlacement) {
        if (adsRemoved()) return
        if (!initialized) {
            initialize()
            return
        }
        if (rewardedAds[placement] != null || !loadingPlacements.add(placement)) return

        RewardedAd.load(
            activity,
            rewardedAdUnitId(placement),
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loadingPlacements.remove(placement)
                    if (!adsRemoved()) {
                        rewardedAds[placement] = ad
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadingPlacements.remove(placement)
                    rewardedAds.remove(placement)
                }
            }
        )
    }

    /**
     * Anchored adaptive banners are permitted only on non-gameplay screens.
     * Production uses the configured live banner unit. DEV and Google Play
     * Internal Testing use Google's sample banner unit so test traffic never
     * touches production inventory.
     */
    fun createBanner(widthDp: Int): AdView? {
        if (adsRemoved() || widthDp < 32) return null
        val useTestAdUnits = activity.resources.getBoolean(R.bool.use_test_ad_units)
        val adUnitId = if (useTestAdUnits) {
            activity.getString(R.string.admob_test_banner)
        } else {
            activity.getString(R.string.admob_banner)
        }
        if (!adUnitId.startsWith("ca-app-pub-") || !adUnitId.contains('/')) return null

        return AdView(activity).apply {
            setAdUnitId(adUnitId)
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp))
        }
    }

    fun loadBanner(adView: AdView) {
        if (!adsRemoved()) adView.loadAd(AdRequest.Builder().build())
    }

    fun isReady(placement: RewardPlacement): Boolean =
        !adsRemoved() && rewardedAds[placement] != null

    fun showRewarded(
        placement: RewardPlacement,
        onRewardEarned: () -> Unit,
        onClosedWithoutReward: () -> Unit,
        onUnavailable: () -> Unit
    ) {
        if (adsRemoved()) {
            onUnavailable()
            return
        }

        val ad = rewardedAds.remove(placement)
        if (ad == null) {
            preload(placement)
            onUnavailable()
            return
        }

        var rewardEarned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preload(placement)
                if (rewardEarned) {
                    onRewardEarned()
                } else {
                    onClosedWithoutReward()
                }
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                preload(placement)
                onUnavailable()
            }
        }

        ad.show(activity) {
            rewardEarned = true
        }
    }

    private fun rewardedAdUnitId(placement: RewardPlacement): String {
        val useTestAdUnits = activity.resources.getBoolean(R.bool.use_test_ad_units)
        if (useTestAdUnits) {
            return activity.getString(R.string.admob_test_rewarded)
        }

        return when (placement) {
            RewardPlacement.PUNCH_CONTINUE ->
                activity.getString(R.string.admob_punch_rewarded_continue)
            RewardPlacement.BEANING_CONTINUE ->
                activity.getString(R.string.admob_beaning_rewarded_continue)
        }
    }
}
