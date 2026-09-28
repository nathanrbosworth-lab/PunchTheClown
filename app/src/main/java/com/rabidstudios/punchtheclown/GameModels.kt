package com.rabidstudios.punchtheclown

enum class GameMode {
    PUNCH,
    BEANING
}

data class GameResult(
    val mode: GameMode,
    val score: Long,
    val level: Int,
    val longestRun: Int,
    val hits: Int,
    val misses: Int = 0,
    val completedRounds: Int = 0
)
