package com.rabidstudios.punchtheclown

import android.app.Activity
import com.google.android.gms.ads.MobileAds

/**
 * M5 monetization foundation.
 *
 * This build initializes Google Mobile Ads and centralizes rewarded-ad IDs.
 * Debug/test APKs always resolve to Google's rewarded test unit so development
 * traffic can never hit the production rewarded placements.
 */
class AdMobManager(
    private val activity: Activity
) {
    enum class RewardPlacement {
        PUNCH_CONTINUE,
        BEANING_CONTINUE
    }

    fun initialize() {
        MobileAds.initialize(activity)
    }

    fun rewardedAdUnitId(placement: RewardPlacement): String {
        if (BuildConfig.DEBUG) {
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
