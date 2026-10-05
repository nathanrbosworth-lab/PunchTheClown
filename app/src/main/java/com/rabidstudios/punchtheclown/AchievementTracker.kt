package com.rabidstudios.punchtheclown

class AchievementTracker(
    private val stats: LocalStatsRepository,
    private val gateway: AchievementGateway
) {

    fun noteGameResult(result: GameResult) {
        if (result.mode != GameMode.BEANING) return

        if (result.hits >= 20 && result.misses == 0) {
            stats.markAchievementEarned(AchievementKey.DEADEYE_AT_THE_MIDWAY)
        }
        if (result.hits >= 50) {
            stats.markAchievementEarned(AchievementKey.CLOWN_CARNAGE)
        }
    }

    fun sync() {
        val punch = stats.punchStats()
        val beaning = stats.beaningStats()
        val totalGames = punch.gamesPlayed + beaning.gamesPlayed

        unlockIf(totalGames >= 1L, AchievementKey.STEP_RIGHT_UP)
        unlockIf(punch.gamesPlayed > 0L && beaning.gamesPlayed > 0L, AchievementKey.DOUBLE_FEATURE)
        gateway.setSteps(AchievementKey.CARNIVAL_REGULAR, steps(totalGames, 25))
        gateway.setSteps(AchievementKey.MIDWAY_VETERAN, steps(totalGames, 100))

        unlockIf(punch.correctPunches >= 1L, AchievementKey.FIRST_PUNCH)
        unlockIf(punch.sequencesCompleted >= 1L, AchievementKey.NO_CLOWNING_AROUND)
        unlockIf(punch.longestSequence >= 5, AchievementKey.GOOD_MEMORY)
        unlockIf(punch.longestSequence >= 10, AchievementKey.STEEL_TRAP)
        unlockIf(punch.longestSequence >= 20, AchievementKey.CLOWN_SAVANT)
        gateway.setSteps(AchievementKey.PUNCH_DRUNK, steps(punch.correctPunches, 100))
        gateway.setSteps(AchievementKey.HEAVY_HITTER, steps(punch.correctPunches, 500))
        gateway.setSteps(AchievementKey.PUNCHING_MACHINE, steps(punch.correctPunches, 1000))

        unlockIf(beaning.totalHits >= 1L, AchievementKey.FIRST_BEANING)
        gateway.setSteps(AchievementKey.BEAN_COUNTER, steps(beaning.totalHits, 100))
        gateway.setSteps(AchievementKey.BEAN_THERE_DONE_THAT, steps(beaning.totalHits, 500))
        gateway.setSteps(AchievementKey.BEAN_MACHINE, steps(beaning.totalHits, 1000))
        unlockIf(beaning.longestRun >= 10, AchievementKey.HOT_STREAK)
        unlockIf(beaning.longestRun >= 25, AchievementKey.ON_A_ROLL)

        unlockIf(
            stats.isAchievementEarned(AchievementKey.DEADEYE_AT_THE_MIDWAY),
            AchievementKey.DEADEYE_AT_THE_MIDWAY
        )
        unlockIf(
            stats.isAchievementEarned(AchievementKey.CLOWN_CARNAGE),
            AchievementKey.CLOWN_CARNAGE
        )
    }

    private fun unlockIf(condition: Boolean, key: AchievementKey) {
        if (condition) gateway.unlock(key)
    }

    private fun steps(value: Long, target: Int): Int =
        value.coerceIn(0L, target.toLong()).toInt()

    private fun steps(value: Int, target: Int): Int =
        value.coerceIn(0, target)
}
