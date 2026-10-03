package com.rabidstudios.punchtheclown

enum class LeaderboardAuthState {
    UNCONFIGURED,
    CHECKING,
    AUTHENTICATED,
    SIGNED_OUT,
    ERROR
}

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
}
