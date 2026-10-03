package com.rabidstudios.punchtheclown

enum class LeaderboardAuthState {
    UNCONFIGURED,
    CHECKING,
    AUTHENTICATED,
    SIGNED_OUT,
    ERROR
}

data class RankedScore(
    val rank: Long,
    val displayRank: String,
    val displayName: String,
    val score: Long,
    val formattedScore: String,
    val isCurrentPlayer: Boolean
)

data class LeaderboardSnapshot(
    val mode: GameMode,
    val top50: List<RankedScore>,
    val playerScore: RankedScore?,
    val nearbyScores: List<RankedScore>
)

interface LeaderboardGateway {
    val isConfigured: Boolean
    val authState: LeaderboardAuthState

    fun refreshAuthentication(
        onResult: (LeaderboardAuthState) -> Unit = {}
    )

    fun requestSignIn(
        onResult: (LeaderboardAuthState) -> Unit = {}
    )

    fun submitScore(
        mode: GameMode,
        score: Long,
        onResult: (Boolean) -> Unit = {}
    )

    fun loadSnapshot(
        mode: GameMode,
        forceReload: Boolean = false,
        onResult: (LeaderboardSnapshot?, Throwable?) -> Unit
    )
}
