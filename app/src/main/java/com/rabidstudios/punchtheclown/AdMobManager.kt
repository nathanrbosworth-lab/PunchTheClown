package com.rabidstudios.punchtheclown

import android.app.Activity
import android.content.pm.ApplicationInfo
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * M5 rewarded-ad manager.
 *
 * Rewarded ads are preloaded and shown only after gameplay has already stopped.
 * Debug/test APKs always use Google's rewarded test unit so development traffic
 * can never hit the production rewarded placements.
 */
class AdMobManager(
    private val activity: Activity
) {
    enum class RewardPlacement {
        PUNCH_CONTINUE,
        BEANING_CONTINUE
    }

    private val rewardedAds = mutableMapOf<RewardPlacement, RewardedAd>()
    private val loadingPlacements = mutableSetOf<RewardPlacement>()

    fun initialize() {
        MobileAds.initialize(activity) {
            preload(RewardPlacement.PUNCH_CONTINUE)
            preload(RewardPlacement.BEANING_CONTINUE)
        }
    }

    fun preload(placement: RewardPlacement) {
        if (rewardedAds[placement] != null || !loadingPlacements.add(placement)) return

        RewardedAd.load(
            activity,
            rewardedAdUnitId(placement),
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loadingPlacements.remove(placement)
                    rewardedAds[placement] = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadingPlacements.remove(placement)
                    rewardedAds.remove(placement)
                }
            }
        )
    }

    fun isReady(placement: RewardPlacement): Boolean =
        rewardedAds[placement] != null

    fun showRewarded(
        placement: RewardPlacement,
        onRewardEarned: () -> Unit,
        onClosedWithoutReward: () -> Unit,
        onUnavailable: () -> Unit
    ) {
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
        val isDebuggable =
            activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (isDebuggable) {
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
