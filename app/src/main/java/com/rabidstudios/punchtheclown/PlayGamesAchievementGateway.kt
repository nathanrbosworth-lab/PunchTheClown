package com.rabidstudios.punchtheclown

import android.app.Activity
import com.google.android.gms.games.PlayGames

class PlayGamesAchievementGateway(
    private val activity: Activity
) : AchievementGateway {

    override fun unlock(key: AchievementKey) {
        val id = configuredId(key) ?: return
        runCatching {
            PlayGames.getAchievementsClient(activity).unlock(id)
        }
    }

    override fun setSteps(key: AchievementKey, steps: Int) {
        if (steps <= 0) return
        val id = configuredId(key) ?: return
        runCatching {
            PlayGames.getAchievementsClient(activity).setSteps(id, steps)
        }
    }

    override fun showAchievements(onComplete: (Boolean) -> Unit) {
        runCatching {
            PlayGames.getAchievementsClient(activity)
                .getAchievementsIntent()
                .addOnSuccessListener { intent ->
                    activity.startActivity(intent)
                    onComplete(true)
                }
                .addOnFailureListener {
                    onComplete(false)
                }
        }.onFailure {
            onComplete(false)
        }
    }

    private fun configuredId(key: AchievementKey): String? {
        val value = when (key) {
            AchievementKey.STEP_RIGHT_UP -> activity.getString(R.string.achievement_step_right_up)
            AchievementKey.DOUBLE_FEATURE -> activity.getString(R.string.achievement_double_feature)
            AchievementKey.CARNIVAL_REGULAR -> activity.getString(R.string.achievement_carnival_regular)
            AchievementKey.MIDWAY_VETERAN -> activity.getString(R.string.achievement_midway_veteran)
            AchievementKey.FIRST_PUNCH -> activity.getString(R.string.achievement_first_punch)
            AchievementKey.NO_CLOWNING_AROUND -> activity.getString(R.string.achievement_no_clowning_around)
            AchievementKey.GOOD_MEMORY -> activity.getString(R.string.achievement_good_memory)
            AchievementKey.STEEL_TRAP -> activity.getString(R.string.achievement_steel_trap)
            AchievementKey.CLOWN_SAVANT -> activity.getString(R.string.achievement_clown_savant)
            AchievementKey.PUNCH_DRUNK -> activity.getString(R.string.achievement_punch_drunk)
            AchievementKey.HEAVY_HITTER -> activity.getString(R.string.achievement_heavy_hitter)
            AchievementKey.PUNCHING_MACHINE -> activity.getString(R.string.achievement_punching_machine)
            AchievementKey.FIRST_BEANING -> activity.getString(R.string.achievement_first_beaning)
            AchievementKey.BEAN_COUNTER -> activity.getString(R.string.achievement_bean_counter)
            AchievementKey.BEAN_THERE_DONE_THAT -> activity.getString(R.string.achievement_bean_there__done_that)
            AchievementKey.BEAN_MACHINE -> activity.getString(R.string.achievement_bean_machine)
            AchievementKey.HOT_STREAK -> activity.getString(R.string.achievement_hot_streak)
            AchievementKey.ON_A_ROLL -> activity.getString(R.string.achievement_on_a_roll)
            AchievementKey.DEADEYE_AT_THE_MIDWAY -> activity.getString(R.string.achievement_deadeye_at_the_midway)
            AchievementKey.CLOWN_CARNAGE -> activity.getString(R.string.achievement_clown_carnage)
        }.trim()

        return value.takeUnless {
            it.isBlank() || it.startsWith(UNCONFIGURED_PREFIX)
        }
    }

    companion object {
        private const val UNCONFIGURED_PREFIX = "UNCONFIGURED_"
    }
}
