package com.rabidstudios.punchtheclown

import android.content.SharedPreferences
import kotlin.math.max

data class PunchStats(
    val highScore: Long,
    val highestLevel: Int,
    val longestSequence: Int,
    val gamesPlayed: Long,
    val correctPunches: Long,
    val sequencesCompleted: Long
)

data class BeaningStats(
    val highScore: Long,
    val highestLevel: Int,
    val totalHits: Long,
    val totalMisses: Long,
    val gamesPlayed: Long,
    val longestRun: Int
)

class LocalStatsRepository(
    private val prefs: SharedPreferences
) {
    fun punchStats(): PunchStats = PunchStats(
        highScore = prefs.getLong(KEY_PUNCH_HIGH_SCORE, 0L),
        highestLevel = prefs.getInt(KEY_PUNCH_HIGHEST_LEVEL, 0),
        longestSequence = prefs.getInt(KEY_PUNCH_LONGEST_SEQUENCE, 0),
        gamesPlayed = prefs.getLong(KEY_PUNCH_GAMES_PLAYED, 0L),
        correctPunches = prefs.getLong(KEY_PUNCH_CORRECT_PUNCHES, 0L),
        sequencesCompleted = prefs.getLong(KEY_PUNCH_SEQUENCES_COMPLETED, 0L)
    )

    fun beaningStats(): BeaningStats = BeaningStats(
        highScore = prefs.getLong(KEY_BEANING_HIGH_SCORE, 0L),
        highestLevel = prefs.getInt(KEY_BEANING_HIGHEST_LEVEL, 0),
        totalHits = prefs.getLong(KEY_BEANING_TOTAL_HITS, 0L),
        totalMisses = prefs.getLong(KEY_BEANING_TOTAL_MISSES, 0L),
        gamesPlayed = prefs.getLong(KEY_BEANING_GAMES_PLAYED, 0L),
        longestRun = prefs.getInt(KEY_BEANING_LONGEST_RUN, 0)
    )

    /**
     * Persists the exact M3 statistics schema and returns true only when this
     * result establishes a new local high score.
     */
    fun record(result: GameResult): Boolean {
        return when (result.mode) {
            GameMode.PUNCH -> recordPunch(result)
            GameMode.BEANING -> recordBeaning(result)
        }
    }

    fun pendingOnlineScore(mode: GameMode): Long =
        prefs.getLong(pendingKey(mode), 0L)

    fun queuePendingOnlineScore(mode: GameMode, score: Long) {
        if (score <= 0L) return
        val key = pendingKey(mode)
        val existing = prefs.getLong(key, 0L)
        if (score > existing) {
            prefs.edit().putLong(key, score).apply()
        }
    }

    fun clearPendingOnlineScore(mode: GameMode, submittedScore: Long) {
        val key = pendingKey(mode)
        val pending = prefs.getLong(key, 0L)
        if (pending in 1..submittedScore) {
            prefs.edit().remove(key).apply()
        }
    }

    private fun recordPunch(result: GameResult): Boolean {
        val old = punchStats()
        val newHigh = result.score > old.highScore
        prefs.edit()
            .putLong(KEY_PUNCH_HIGH_SCORE, max(old.highScore, result.score))
            .putInt(KEY_PUNCH_HIGHEST_LEVEL, max(old.highestLevel, result.level))
            .putInt(KEY_PUNCH_LONGEST_SEQUENCE, max(old.longestSequence, result.longestRun))
            .putLong(KEY_PUNCH_GAMES_PLAYED, old.gamesPlayed + 1L)
            .putLong(KEY_PUNCH_CORRECT_PUNCHES, old.correctPunches + result.hits)
            .putLong(KEY_PUNCH_SEQUENCES_COMPLETED, old.sequencesCompleted + result.completedRounds)
            .apply()
        return newHigh
    }

    private fun recordBeaning(result: GameResult): Boolean {
        val old = beaningStats()
        val newHigh = result.score > old.highScore
        prefs.edit()
            .putLong(KEY_BEANING_HIGH_SCORE, max(old.highScore, result.score))
            .putInt(KEY_BEANING_HIGHEST_LEVEL, max(old.highestLevel, result.level))
            .putInt(KEY_BEANING_LONGEST_RUN, max(old.longestRun, result.longestRun))
            .putLong(KEY_BEANING_GAMES_PLAYED, old.gamesPlayed + 1L)
            .putLong(KEY_BEANING_TOTAL_HITS, old.totalHits + result.hits)
            .putLong(KEY_BEANING_TOTAL_MISSES, old.totalMisses + result.misses)
            .apply()
        return newHigh
    }

    private fun pendingKey(mode: GameMode): String = when (mode) {
        GameMode.PUNCH -> KEY_PENDING_PUNCH_SCORE
        GameMode.BEANING -> KEY_PENDING_BEANING_SCORE
    }

    companion object {
        private const val KEY_PUNCH_HIGH_SCORE = "high_score"
        private const val KEY_PUNCH_HIGHEST_LEVEL = "highest_level"
        private const val KEY_PUNCH_LONGEST_SEQUENCE = "longest_sequence"
        private const val KEY_PUNCH_GAMES_PLAYED = "games_played"
        private const val KEY_PUNCH_CORRECT_PUNCHES = "correct_punches"
        private const val KEY_PUNCH_SEQUENCES_COMPLETED = "sequences_completed"

        private const val KEY_BEANING_HIGH_SCORE = "beaning_high_score"
        private const val KEY_BEANING_HIGHEST_LEVEL = "beaning_highest_level"
        private const val KEY_BEANING_TOTAL_HITS = "beaning_total_hits"
        private const val KEY_BEANING_TOTAL_MISSES = "beaning_total_misses"
        private const val KEY_BEANING_GAMES_PLAYED = "beaning_games_played"
        private const val KEY_BEANING_LONGEST_RUN = "beaning_longest_run"

        private const val KEY_PENDING_PUNCH_SCORE = "pending_online_punch_score"
        private const val KEY_PENDING_BEANING_SCORE = "pending_online_beaning_score"
    }
}
